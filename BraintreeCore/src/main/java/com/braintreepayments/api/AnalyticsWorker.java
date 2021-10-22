package com.braintreepayments.api;

import static com.braintreepayments.api.AnalyticsClient.WORK_INPUT_KEY_AUTHORIZATION;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public abstract class AnalyticsWorker extends Worker {

    public AnalyticsWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    protected AnalyticsClient createAnalyticsClientFromInputData() {
        Data inputData = getInputData();
        String authString = inputData.getString(WORK_INPUT_KEY_AUTHORIZATION);
        Authorization authorization = Authorization.fromString(authString);
        return new AnalyticsClient(authorization);
    }
}
