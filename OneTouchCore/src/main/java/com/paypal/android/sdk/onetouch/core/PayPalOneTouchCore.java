package com.paypal.android.sdk.onetouch.core;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.MainThread;

import com.paypal.android.sdk.onetouch.core.base.ContextInspector;
import com.paypal.android.sdk.onetouch.core.base.SdkRiskComponent;
import com.paypal.android.sdk.onetouch.core.config.ConfigManager;
import com.paypal.android.sdk.onetouch.core.config.OtcConfiguration;
import com.paypal.android.sdk.onetouch.core.config.Recipe;
import com.paypal.android.sdk.onetouch.core.enums.Protocol;
import com.paypal.android.sdk.onetouch.core.enums.RequestTarget;
import com.paypal.android.sdk.onetouch.core.fpti.FptiManager;
import com.paypal.android.sdk.onetouch.core.fpti.TrackingPoint;
import com.paypal.android.sdk.onetouch.core.network.EnvironmentManager;
import com.paypal.android.sdk.onetouch.core.network.PayPalHttpClient;
import com.paypal.android.sdk.onetouch.core.sdk.AppSwitchHelper;
import com.paypal.android.sdk.onetouch.core.sdk.BrowserSwitchHelper;
import com.paypal.android.sdk.onetouch.core.sdk.PendingRequest;
import com.paypal.android.sdk.onetouch.core.sdk.V1WalletHelper;
import com.paypal.android.sdk.onetouch.core.sdk.V2WalletHelper;

import java.util.Collections;

/**
 * Central class for One Touch functionality.
 * <p>
 * Note that all public methods should call initService which starts the config file querying
 */
public class PayPalOneTouchCore {

    private static ContextInspector sContextInspector;
    private static ConfigManager sConfigManager;
    private static FptiManager sFptiManager;

    public static void useHardcodedConfig(Context context, boolean useHardcodedConfig) {
        initService(context);
        sConfigManager.useHardcodedConfig(useHardcodedConfig);
    }

    public static FptiManager getFptiManager(Context context) {
        initService(context);
        return sFptiManager;
    }

    private static void initService(Context context) {
        if (sConfigManager == null || sFptiManager == null) {
            PayPalHttpClient httpClient = new PayPalHttpClient()
                    .setBaseUrl(EnvironmentManager.LIVE_API_M_ENDPOINT);
            sConfigManager = new ConfigManager(getContextInspector(context), httpClient);
            sFptiManager = new FptiManager(getContextInspector(context), httpClient);
        }

        // always refresh configuration
        sConfigManager.refreshConfiguration();
    }

    /**
     * Return true if the modern wallet app is installed (one that has either v1 or v2 touch
     * intents). Returns false if the wallet app is older than the touch releases, or not present.
     */
    public static boolean isWalletAppInstalled(Context context, boolean enableSecurityCheck) {
        initService(context);

        boolean isV2WalletAppInstalled = new V2WalletHelper()
                .isValidV2TouchAuthenticatorInstalled(context, enableSecurityCheck);
        sFptiManager.trackFpti((isV2WalletAppInstalled) ? TrackingPoint.WalletIsPresent :
                        TrackingPoint.WalletIsAbsent, ""/*no environment set yet*/,
                Collections.<String, String>emptyMap(), Protocol.v2);

        boolean isV1WalletAppInstalled = false;
        if (!isV2WalletAppInstalled) {
            isV1WalletAppInstalled = new V1WalletHelper()
                    .isValidV1TouchAuthenticatorInstalled(context, enableSecurityCheck);
            sFptiManager.trackFpti((isV1WalletAppInstalled) ? TrackingPoint.WalletIsPresent :
                            TrackingPoint.WalletIsAbsent, ""/*no environment set yet*/,
                    Collections.<String, String>emptyMap(), Protocol.v1);
        }
        return isV2WalletAppInstalled || isV1WalletAppInstalled;
    }

    /**
     * Get an {@link PendingRequest} containing an {@link Intent} used to start a PayPal
     * authentication request using the best possible authentication mechanism: wallet or browser.
     *
     * @param context
     * @param request the {@link Request} used to build the {@link Intent}.
     * @param enableSecurityCheck
     * @return {@link PendingRequest}. {@link PendingRequest#isSuccess()} should be
     * checked before attempting to use the {@link Intent} it contains. If {@code true} an
     * {@link Intent} was created and can be used to start a PayPal authentication request. If
     * {@code false} it is not possible to authenticate given the current environment and request.
     */
    public static PendingRequest getStartIntent(Context context, Request request,
            boolean enableSecurityCheck) {
        initService(context);

        // calling this method functionally does nothing, but ensures that we send off FPTI data about wallet installs.
        isWalletAppInstalled(context, enableSecurityCheck);

        Recipe recipe = request.getRecipeToExecute(context, getConfig(context),
                enableSecurityCheck);

        if (recipe == null) {
            return new PendingRequest(false, null, null, null);
        }

        // Set CMID for Single Payment and Billing Agreements
        if (request instanceof BillingAgreementRequest) {
            request.clientMetadataId(PayPalOneTouchCore.getClientMetadataId(context,
                    ((BillingAgreementRequest) request).getPairingId()));
        } else if (request instanceof CheckoutRequest) {
            request.clientMetadataId(PayPalOneTouchCore.getClientMetadataId(context,
                    ((CheckoutRequest) request).getPairingId()));
        }

        if (RequestTarget.wallet == recipe.getTarget()) {
            request.trackFpti(context, TrackingPoint.SwitchToWallet, recipe.getProtocol());
            return new PendingRequest(true, RequestTarget.wallet,
                    request.getClientMetadataId(),
                    AppSwitchHelper.getAppSwitchIntent(sContextInspector, sConfigManager, request,
                            recipe.getProtocol()));
        } else {
            Intent intent = BrowserSwitchHelper
                    .getBrowserSwitchIntent(sContextInspector, sConfigManager,
                    request);
            if (intent != null) {
                return new PendingRequest(true, RequestTarget.browser,
                        request.getClientMetadataId(), intent);
            } else {
                return new PendingRequest(false, RequestTarget.browser,
                        request.getClientMetadataId(), null);
            }
        }
    }

    /**
     * Gets a {@link Result} from an {@link Intent} returned by either the PayPal Wallet app or
     * the browser.
     *
     * @param context
     * @param request the original {@link Request} that was used to get this {@link Result}.
     * @param data the {@link Intent} returned by either the PayPal Wallet app or the browser.
     * @return
     */
    public static Result parseResponse(Context context, Request request, Intent data) {
        initService(context);

        if (data != null && data.getData() != null) {
            return BrowserSwitchHelper.parseBrowserSwitchResponse(sContextInspector, request,
                    data.getData());
        } else if (data != null && data.getExtras() != null && !data.getExtras().isEmpty()) {
            return AppSwitchHelper.parseAppSwitchResponse(sContextInspector, request, data);
        } else {
            request.trackFpti(context, TrackingPoint.Cancel, null);
            return new Result();
        }
    }

    /**
     * Gets a Client Metadata ID at the time of future payment activity. Once a user has consented
     * to future payments, when the user subsequently initiates a PayPal payment from their device
     * to be completed by your server, PayPal uses a Correlation ID to verify that the payment is
     * originating from a valid, user-consented device+application. This helps reduce fraud and
     * decrease declines. This method MUST be called prior to initiating a pre-consented payment (a
     * "future payment") from a mobile device. Pass the result to your server, to include in the
     * payment request sent to PayPal. Do not otherwise cache or store this value.
     *
     * @param context The application context
     * @return The applicationCorrelationID - Your server will send this to PayPal in a
     * 'PayPal-Client-Metadata-Id' header.
     */
    @MainThread
    public static String getClientMetadataId(Context context) {
        return getClientMetadataId(context, null);
    }

    /**
     * Gets a Client Metadata ID at the time of future payment activity. Once a user has consented
     * to future payments, when the user subsequently initiates a PayPal payment from their device
     * to be completed by your server, PayPal uses a Correlation ID to verify that the payment is
     * originating from a valid, user-consented device+application. This helps reduce fraud and
     * decrease declines. This method MUST be called prior to initiating a pre-consented payment (a
     * "future payment") from a mobile device. Pass the result to your server, to include in the
     * payment request sent to PayPal. Do not otherwise cache or store this value.
     *
     * @param context The application context
     * @param pairingId The desired pairing id
     * @return The applicationCorrelationID - Your server will send this to PayPal in a
     * 'PayPal-Client-Metadata-Id' header.
     */
    @MainThread
    public static String getClientMetadataId(Context context, String pairingId) {
        return SdkRiskComponent.getClientMetadataId(context,
                getContextInspector(context).getInstallationGUID(), BuildConfig.PRODUCT_VERSION,
                pairingId);
    }

    /**
     * Returns the currently active config.
     */
    static OtcConfiguration getConfig(Context context) {
        initService(context);
        return sConfigManager.getConfig();
    }

    /**
     * Lazily creates and returns a sContextInspector object
     */
    static ContextInspector getContextInspector(Context context) {
        if (null == sContextInspector) {
            sContextInspector = new ContextInspector(context);
        }
        return sContextInspector;
    }
}
