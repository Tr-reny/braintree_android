package com.braintreepayments.demo;

import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import android.widget.ArrayAdapter;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import java.util.Arrays;
import java.util.List;

public class DemoActivity extends AppCompatActivity implements ActivityCompat.OnRequestPermissionsResultCallback, ActionBar.OnNavigationListener {

    private AppBarConfiguration appBarConfiguration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.activity_demo);

        setupActionBar();
        setProgressBarIndeterminateVisibility(true);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (BuildConfig.DEBUG && ContextCompat.checkSelfPermission(this, WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{WRITE_EXTERNAL_STORAGE}, 1);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    private NavController getNavController() {
        Fragment navHostFragment = getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
        return NavHostFragment.findNavController(navHostFragment);
    }

    private void setupActionBar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        NavController navController = getNavController();
        appBarConfiguration = new AppBarConfiguration.Builder(navController.getGraph()).build();
        NavigationUI.setupWithNavController(toolbar, navController, appBarConfiguration);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(false);
            actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_LIST);

            ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, R.array.environments, android.R.layout.simple_spinner_dropdown_item);
            actionBar.setListNavigationCallbacks(adapter, this);

            List<String> envs = Arrays.asList(getResources().getStringArray(R.array.environments));
            actionBar.setSelectedNavigationItem(envs.indexOf(Settings.getEnvironment(this)));
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = getNavController();
        return NavigationUI.navigateUp(navController, appBarConfiguration)
                || super.onSupportNavigateUp();
    }

    public void showDialog(String message) {
        new AlertDialog.Builder(this)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> dialog.dismiss())
                .show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.settings:
                NavController navController = getNavController();
                navController.navigate(R.id.open_settings_fragment);
                return true;
            default:
                return false;
        }
    }

    public boolean onNavigationItemSelected(int itemPosition, long itemId) {
        String env = getResources().getStringArray(R.array.environments)[itemPosition];
        if (!Settings.getEnvironment(this).equals(env)) {
            Settings.setEnvironment(this, env);
        }
        return true;
    }
}