package com.circleof6.ui;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.support.annotation.DrawableRes;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.DialogFragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.telephony.SmsManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import com.android.volley.toolbox.ImageLoader;
import com.android.volley.toolbox.NetworkImageView;
import com.circleof6.CircleOf6Application;
import com.circleof6.R;
import com.circleof6.data.DBHelper;
import com.circleof6.data.DBUpdateService;
import com.circleof6.data.DBUpdateServicePhaseTwo;
import com.circleof6.dialog.ContactNumbersDialog;
import com.circleof6.dialog.EmergencyPhonesDialog;
import com.circleof6.dialog.InformationLinksDialog;
import com.circleof6.dialog.OverlayDialog;
import com.circleof6.dialog.SendSmsAlertDialog;
import com.circleof6.dialog.UnsentSMSDialog;
import com.circleof6.dialog.utils.ConstantsDialog;
import com.circleof6.dialog.utils.TypeSendSmsListener;
import com.circleof6.model.CollegeCountry;
import com.circleof6.model.Contact;
import com.circleof6.model.SMS;
import com.circleof6.preferences.AppPreferences;
import com.circleof6.receiver.SentSMSReceiver;
import com.circleof6.receiver.ShareNotificationReceiver;
import com.circleof6.util.Constants;
import com.circleof6.util.ConstantsAnalytics;
import com.circleof6.view.CircleOf6View;
import com.circleof6.view.util.DrawUtils;
import com.circleof6.view.util.OnClickListenerCircleOf6View;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.wang.avi.AVLoadingIndicatorView;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;


import cn.pedant.SweetAlert.SweetAlertDialog;

import static com.circleof6.CircleOf6Application.isUniversalFlavor;
import static com.circleof6.util.MethodsUtils.getPhotoFileByContact;


/*
 * Depends on https://github.com/codinguser/android_contact_picker.git
 * 
 */
public class MainActivity extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, com.google.android.gms.location.LocationListener, OnClickListenerCircleOf6View, TypeSendSmsListener {

    //~=~=~=~=~=~=~=~=~=~=~=~=Constants
    private static final String LOG_TAG = MainActivity.class.getSimpleName();

    //~=~=~=~=~=~=~=~=~=~=~=~=Fields
    private GoogleApiClient mGoogleApiClient;
    private ArrayList<Contact> contacts;
    private SentSMSReceiver broadcastReceiverSentSMS;
    private boolean isRegisterReceiver;
    private Set<Integer> contactsPicked;
    private CircleOf6View circleOf6View;
    private boolean showAlertSentMessageSuccess;
    private int numMessagesSent;
    private int numSmsSent;
    private boolean mRequestingLocationUpdates;
    private boolean isStopActivity;
    private Timer timer;
    private AVLoadingIndicatorView progressBar;
    private FrameLayout fullBody;
    private DBHelper dbHelper;
    private List<SMS> sms;

    //----------------------------------------------------------------LifeCycle
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        dbHelper = new DBHelper(MainActivity.this);

        progressBar = (AVLoadingIndicatorView) findViewById(R.id.progressBar);
        fullBody = (FrameLayout) findViewById(R.id.full_body);

        progressBar.setVisibility(View.VISIBLE);
        fullBody.setVisibility(View.GONE);

        connectGoogleApiClient();

        sms = dbHelper.getSMS();
        init();


        askForContactPermission(Manifest.permission.SEND_SMS);
        askForContactPermission(Manifest.permission.READ_PHONE_STATE);
        askForContactPermission(Manifest.permission.ACCESS_FINE_LOCATION);

    }

    /**
    public void startContentFetchTimer() {

        stopService(new Intent(getApplicationContext(), DBUpdateServicePhaseTwo.class));
        startService(new Intent(getApplicationContext(), DBUpdateServicePhaseTwo.class));

        timer = new Timer();
        //start timer task to only load tabs after we have content
        TimerTask timerTask = new TimerTask() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        //check if content is available
                        if (hasAllRequiredContent()) {
                            timer.cancel();
                            timer.purge();
                            sms = dbHelper.getSMS();
                            init();
                        }
                    }
                });
            }
        };
        timer.scheduleAtFixedRate(timerTask, 0, 3000);
    }
**/

    public boolean hasAllRequiredContent() {

        boolean hasAll = true;

        List<String> requiredTables = new ArrayList<>();

        requiredTables.add(DBHelper.TABLE_CUSTOM_NUMBER);
        requiredTables.add(DBHelper.TABLE_SMS);
        requiredTables.add(DBHelper.TABLE_WEB_RESOURCES);

        for (String table : requiredTables) {
            if (!contentLoaded(table)) {
                hasAll = false;
            }else{
                Log.d("no content", "no content" + table);
            }
        }

        boolean service_running = CircleOf6Application.isServiceRunning(DBUpdateServicePhaseTwo.class);

        if (!service_running && !hasAll) {
            if (timer != null) {
                timer.cancel();
                timer.purge();
            }

            //service is stopped because of fetch error
            //startContentFetchTimer();
        }

        return hasAll;
    }

    /**
    public void showRetryDialog(final boolean secondPhase) {
        new SweetAlertDialog(this, SweetAlertDialog.WARNING_TYPE)
                .setTitleText(getResources().getString(R.string.no_posts))
                .setContentText(getResources().getString(R.string.no_posts_description))
                .setConfirmText(getResources().getString(R.string.retry))
                .setConfirmClickListener(new SweetAlertDialog.OnSweetClickListener() {
                    @Override
                    public void onClick(SweetAlertDialog sDialog) {
                        sDialog.dismissWithAnimation();
                        //startContentFetchTimer();
                    }
                })
                .show();
    }**/

    public boolean contentLoaded(String table_name) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        return preferences.getBoolean(table_name, false);
    }


    public void init() {
        fullBody.setVisibility(View.VISIBLE);
        progressBar.setVisibility(View.GONE);

        setupViews();
        buildGoogleApiClient();

        if (AppPreferences.getInstance(this).hasCompletedTutorial()) {
            createNotificationShare();
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        connectGoogleApiClient();

        if (showAlertSentMessageSuccess) {
            showSuccessDialog();
        }
        isStopActivity = false;

        //check if has picked uni
        /**
        if (AppPreferences.getInstance(MainActivity.this)
                .getCollegeLocation().equals("")) {
            launchPickCollegeOrCountryActivity();
        }else{
         **/
        if (!AppPreferences.getInstance(MainActivity.this).hasCompletedTutorial()) {
                launchPagedTutorial();
        }

    }


    @Override
    protected void onStop() {
        super.onStop();
        disconnectGoogleApiClient();
        isStopActivity = true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopLocationUpdates();
        disconnectGoogleApiClient();
        unregisterReceiverSentSms();
    }

    @Override
    public void onActivityResult(int reqCode, int resultCode, Intent data) {
        super.onActivityResult(reqCode, resultCode, data);

        switch (reqCode) {
            case Constants.FRIEND_ONE_INTENT:
            case Constants.FRIEND_TWO_INTENT:
            case Constants.FRIEND_THREE_INTENT:
            case Constants.FRIEND_FOUR_INTENT:
            case Constants.FRIEND_FIVE_INTENT:
            case Constants.FRIEND_SIX_INTENT:
                if (resultCode == Activity.RESULT_OK) {
                    updateCircle(reqCode, data);
                }
                break;

            case Constants.PAGED_TUTORIAL_INTENT:
                if (!AppPreferences.getInstance(this).hasCompletedTutorial()) {
                    finish();
                } else {
                    createNotificationShare();
                    init();
                    if (!AppPreferences.getInstance(this).isShowAllowContactDialog()) {
                        displayAllowedContactsDialog();
                    }
                }
                break;

            case Constants.PICK_COLLEGE_INTENT:
                if (!AppPreferences.getInstance(this).hasCompletedTutorial()) {
                    if (resultCode == RESULT_OK) {
                        launchPagedTutorial();
                    } else {
                        finish();

                    }
                }

                break;
            case Constants.REQUEST_PLAY_SERVICES_ERROR:

                if (resultCode == RESULT_OK) {
                    connectGoogleApiClient();
                }
                break;
        }

    }


    //----------------------------------------------------------------Init
    public void setupViews() {
        broadcastReceiverSentSMS = new SentSMSReceiver();
        isRegisterReceiver = false;
        showAlertSentMessageSuccess = false;
        mRequestingLocationUpdates = false;

        //Circle of 6 view
        circleOf6View = (CircleOf6View) findViewById(R.id.circle_of_6);
        circleOf6View.getViewTreeObserver()
                .addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        circleOf6View.setOnClickListener(MainActivity.this);
                    }
                });
        circleOf6View.refreshDrawableState();

        setUpView();
        setupInfoContacts();
    }

    private void setupInfoContacts() {
        contactsPicked = new HashSet<>();
        contacts = getContactsFromPreferences();
        circleOf6View.setContacts(contacts);

        if (contactsPicked.size() < 3) {
            circleOf6View.setIsTextAddContacts(true);
            findViewById(R.id.checkbutton).setVisibility(View.GONE);
        } else {
            circleOf6View.setIsTextAddContacts(false);
            findViewById(R.id.checkbutton).setVisibility(View.VISIBLE);
        }

    }

    private ArrayList<Contact> getContactsFromPreferences() {

        ArrayList<Contact> savedFriends = new ArrayList<>();

        for (int idContact = 1; idContact <= Constants.MAX_NUMBER_OF_FRIENDS; idContact++) {
            String name = AppPreferences.getInstance(this).getNameContact(idContact);
            String phone = AppPreferences.getInstance(this).getPhoneContact(idContact);
            String photo = AppPreferences.getInstance(this).getPhotoContact(idContact);
            Contact contact = new Contact(idContact, name, phone, photo);
            savedFriends.add(contact);
            if (!contact.isEmpty()) {
                contactsPicked.add(contact.getId());
            }
        }

        return savedFriends;
    }

    public void setUpView() {
        if (isUniversalFlavor()) {
            setupUniversal();
        } else {
            setUpCollege();
        }

    }

    public void setupUniversal() {
        findViewById(R.id.logo).setVisibility(View.GONE);
    }

    public void setUpCollege() {
        //Default 911 is off
        circleOf6View.setEnableButton911(false);

        //Get the set college
        NetworkImageView logo = (NetworkImageView) findViewById(R.id.logo);
        CircleOf6Application.getInstance().setUpLogo(logo, dbHelper);

    }


    //----------------------------------------------------------------Listeners
    @Override
    public void locationClicked() {
        showSendSmsAlertDialog(ConstantsDialog.TypeSmsAlertDialog.COME_AND_GETME);
    }

    @Override
    public void phoneClicked() {
        showSendSmsAlertDialog(ConstantsDialog.TypeSmsAlertDialog.CALL_ME_NEED_INTERRUPTION);
    }

    @Override
    public void messageClicked() {
        showSendSmsAlertDialog(ConstantsDialog.TypeSmsAlertDialog.NEED_TO_TALK);
    }

    public void okButtonPress(View view) {
        showSendSmsAlertDialog(ConstantsDialog.TypeSmsAlertDialog.I_AM_OK);
    }

    public void onInfoButtonClicked(View view) {
        launchPagedTutorial();
    }

    @SuppressWarnings("unused")
    public void shareApp(View view) {
        Intent intent = new Intent(this, SharingAppActivity.class);
        startActivity(intent);
    }

    @Override
    public void informationClicked() {
        InformationLinksDialog informationLinksDialog = new InformationLinksDialog();
        informationLinksDialog.show(getSupportFragmentManager(), "dialog_information_link");
    }

    @Override
    public void button911Clicked() {
        try {
            Intent callIntent = new Intent(Intent.ACTION_CALL);
            callIntent.setData(Uri.parse("tel://911"));
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            startActivity(callIntent);
        }
        catch(ActivityNotFoundException e)
        {
            Log.e("call", "Call failed", e);
        }

    }

    public void emergencyButtonPress(View view)
    {
        EmergencyPhonesDialog emergencyPhonesDialog = new EmergencyPhonesDialog();
        emergencyPhonesDialog.show(getSupportFragmentManager(), "dialog_emergency");
    }

    @Override
    public void contactClicked(int position)
    {
        if (!askForContactPermission(Manifest.permission.READ_CONTACTS)) {
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType(ContactsContract.Contacts.CONTENT_TYPE);

            startActivityForResult(intent, position + 1);
        }
    }

    //----------------------------------------------------------------SMS
    @Override
    public void sendSmsComeAndGetMe()
    {
        beginLocationUpdates();
    }

    public void showGettingLocationAlert()
    {
        Log.d(LOG_TAG, "showGettingLocationAlert");
        final OverlayDialog dialog = OverlayDialog.createDialog(getString(R.string.alert_getting_location),
                                                                OverlayDialog.Type.Progress);
        dialog
                .setPressOkSendSmsListener(new OverlayDialog.PressOkSendSmsListener() {
                    @Override
                    public void onPressOkSendSms() {
                        Log.d(LOG_TAG, "onDismissGettingLocation");
                        stopLocationUpdates();
                    }
                });
        dialog
                .show(getSupportFragmentManager(), ConstantsDialog.PROGRESS_LOCATION_DIALOG);
    }

    public void dismissGettingLocationAlert()
    {
        DialogFragment dialogFragment = (DialogFragment) getSupportFragmentManager()
                .findFragmentByTag(ConstantsDialog.PROGRESS_LOCATION_DIALOG);
        if(dialogFragment != null)
        {
            Log.d(LOG_TAG, "dismissGettingLocationAlert");
            dialogFragment.dismiss();
        }
    }

    public String checkCustom(String action, String original){
        for(SMS _sms: sms){
            if(_sms.getLabel().equals(action)){
                return _sms.getText();
            }
        }
        return original;
    }

    @Override
    public void sendSmsCallMeNeed()
    {
        CircleOf6Application
                .defaultLabelTrackingEvent(this, ConstantsAnalytics.ACTION_GET_INTERRUPTION);

        String smsBody = checkCustom(ConstantsAnalytics.ACTION_GET_INTERRUPTION, getString(R.string.middle_button_message));

        sendSMSWithBody(smsBody + " " +
                getString(R.string.message_footer));
    }

    @Override
    public void sendSmsNeedToTalk()
    {
        CircleOf6Application
                .defaultLabelTrackingEvent(this, ConstantsAnalytics.ACTION_NEED_TO_TALK);

        String smsBody = checkCustom(ConstantsAnalytics.ACTION_NEED_TO_TALK, getString(R.string.right_button_message));

        sendSMSWithBody(smsBody + " " +
                getString(R.string.message_footer));
    }

    @Override
    public void sendSmsIamOk()
    {
        CircleOf6Application.defaultLabelTrackingEvent(this, ConstantsAnalytics.ACTION_IM_OK);

        String smsBody = checkCustom(ConstantsAnalytics.ACTION_IM_OK, getString(R.string.ok_button_message));

        sendSMSWithBody(smsBody + " " +
                getString(R.string.message_footer));
    }

    private void showSendSmsAlertDialog(ConstantsDialog.TypeSmsAlertDialog typeSmsAlertDialog)
    {
        SendSmsAlertDialog.newInstance(typeSmsAlertDialog)
                          .show(getSupportFragmentManager(), "dialog_send_sms_alert");
    }

    //Hacer mas pequeño
    public void sendSMSWithBody(String messageString)
    {

        Log.d(LOG_TAG, messageString);

        showAlertSentMessageSuccess = false;
        numMessagesSent = 0;
        numSmsSent = 0;

        SmsManager sm = SmsManager.getDefault();

        Set<String> sentNumbers = new HashSet<>();

        for(int i = 0; i < contacts.size(); i++)
        {

            String uniqueNumber = contacts.get(i).getPhoneNumber();

            if(! sentNumbers.contains(uniqueNumber))
            {
                sentNumbers.add(uniqueNumber);
            }
            else
            {
                continue;
            }

            uniqueNumber = cleanNumber(uniqueNumber);

            if(! TextUtils.isEmpty(uniqueNumber))
            {

                numSmsSent++;
                isRegisterReceiver = true;

                String action = Constants.ACTION_SENT_SMS + "-" + contacts.get(i)
                                                                          .getPhoneNumber() + "-" + contacts
                        .get(i).getName();

                PendingIntent sentPendingIntent = PendingIntent
                        .getBroadcast(this, 0, new Intent(action), 0);

                registerReceiver(broadcastReceiverSentSMS, new IntentFilter(action));

                ArrayList<String> splitMessage = sm.divideMessage(messageString);

                for(int p = 0; p < splitMessage.size(); p++)
                {

                    if(p == 0)
                    {
                        sm.sendTextMessage(uniqueNumber, null, splitMessage.get(p),
                                           sentPendingIntent, null);
                    }
                    else
                    {
                        sm.sendTextMessage(uniqueNumber, null, splitMessage.get(p), null, null);
                    }
                }

            }
        }
    }

    private String cleanNumber(String uniqueNumber)
    {
        if(TextUtils.isEmpty(uniqueNumber))
        {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < uniqueNumber.length(); i++)
        {
            char c = uniqueNumber.charAt(i);
            if(Character.isDigit(c))
            {
                sb.append(c);
            }
        }

        return sb.toString();
    }

    public void showUnsentSMSDialog(String name)
    {
        numMessagesSent++;
        UnsentSMSDialog dialogUnsentSMS = UnsentSMSDialog.newInstance(name);
        dialogUnsentSMS.show(getSupportFragmentManager(), null);
    }

    public void sendSMSNewContact(String name, String phone, String messageString)
    {
        Log.d("sendSMSNewContact", messageString);
        if (!TextUtils.isEmpty(phone)) {

            SmsManager smsManager = SmsManager.getDefault();

            isRegisterReceiver = true;

            String action = Constants.ACTION_SENT_SMS_CONTACT + "-" + name;
            PendingIntent sentPendingIntent = PendingIntent
                    .getBroadcast(this, 0, new Intent(action), 0);

            registerReceiver(broadcastReceiverSentSMS, new IntentFilter(action));

            ArrayList<String> splitMessage = smsManager.divideMessage(messageString);

            for (int p = 0; p < splitMessage.size(); p++) {
                if (p == 0) {
                    smsManager.sendTextMessage(phone, null, splitMessage.get(p), sentPendingIntent,
                            null);
                } else {
                    smsManager.sendTextMessage(phone, null, splitMessage.get(p), null, null);
                }
            }
        }

    }

    private void unregisterReceiverSentSms()
    {
        if(isRegisterReceiver)
        {
            unregisterReceiver(broadcastReceiverSentSMS);
        }
    }


    private void launchPagedTutorial()
    {
        Intent pagedTutorialIntent = PagedTutorialActivity.getInstance(this);
        startActivityForResult(pagedTutorialIntent, Constants.PAGED_TUTORIAL_INTENT);
    }

    public void displayAllowedContactsDialog()
    {
        final OverlayDialog dialog = OverlayDialog.createDialog(getString(R.string.alert_3_contacts));

        dialog
                .show(getSupportFragmentManager(), "dialog_contacts");
    }

    //TODO: Logic for this really shouldn't be here, but to avoid lots of recoding, just let it go
    private void createNotificationShare()
    {
        /**
        if(! AppPreferences.getInstance(this).isCreateNotificationShare())
        {
            Calendar calendar = geDateShareNotification();
            ShareNotificationReceiver
                    .createAlarmNotificationShare(this, calendar.getTimeInMillis());
            ShareNotificationReceiver.enableCircleOfSixBootReceiver(this);
            saveDateShareNotification(calendar);
            AppPreferences.getInstance(this).setCreateNotifcation(true);
        }**/
    }

    public void enableCircleAction()
    {
        ImageView okButton = (ImageView) findViewById(R.id.checkbutton);
        okButton.setVisibility(View.VISIBLE);
        circleOf6View.setIsTextAddContacts(false);
    }

    public void displayNewContact()
    {
        if(! isStopActivity)
        {
            showSuccessDialog();
        }
        else
        {
            showAlertSentMessageSuccess = true;
        }
    }

    //----------------------------------------------------------------LOCATION / Google Api Client
    private void buildGoogleApiClient()
    {
        mGoogleApiClient = new GoogleApiClient.Builder(this).addConnectionCallbacks(this)
                                                            .addOnConnectionFailedListener(this)
                                                            .addApi(LocationServices.API).build();
    }

    private void connectGoogleApiClient()
    {
        if(mGoogleApiClient != null) {
            if (!mGoogleApiClient.isConnected() && !mGoogleApiClient.isConnecting()) {
                mGoogleApiClient.connect();
            }
        }
    }

    public void beginLocationUpdates()
    {
        if(isLocationProviderEnabled())
        {
            showGettingLocationAlert();

            if(mGoogleApiClient != null) {
                if (mGoogleApiClient.isConnected()) {
                    startLocationUpdates();
                } else if (mGoogleApiClient.isConnecting()) {
                    mRequestingLocationUpdates = true;
                } else {
                    mRequestingLocationUpdates = true;
                    mGoogleApiClient.connect();
                }
            }
        }
        else
        {
            showSendSmsGpsDeactivated();
        }
    }

    private boolean isLocationProviderEnabled()
    {
        LocationManager locationManager = (LocationManager) this
                .getSystemService(Context.LOCATION_SERVICE);

        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager
                .isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }

    private void showSendSmsGpsDeactivated()
    {
        final OverlayDialog dialog = OverlayDialog.createDialog(getString(R.string.alert_nogps));
        dialog.setPressOkSendSmsListener(new OverlayDialog.PressOkSendSmsListener()
        {
            @Override
            public void onPressOkSendSms()
            {
                String smsBody = checkCustom(ConstantsAnalytics.ACTION_COME_AND_GET_ME, getString(R.string.left_button_error_message));

                sendSMSWithBody(smsBody + " " + getString(
                        R.string.message_footer_short));
            }
        });
        dialog.show(getSupportFragmentManager(), "dialog_gps_deactivated");
    }

    private void tryResolvedConnectionGPS(ConnectionResult connectionResult)
    {
        if(connectionResult.hasResolution())
        {
            try
            {
                connectionResult
                        .startResolutionForResult(this, Constants.REQUEST_PLAY_SERVICES_ERROR);
            }
            catch(IntentSender.SendIntentException e)
            {
                mGoogleApiClient.connect();
            }
        }
        else
        {
            showDialogErrorPlayServices(connectionResult.getErrorCode());
        }
    }

    private void showDialogErrorPlayServices(int errorCode)
    {
        Dialog dialog = GoogleApiAvailability.getInstance().getErrorDialog(this, errorCode,
                                                                           Constants.REQUEST_PLAY_SERVICES_ERROR);
        dialog.show();
    }

    public void onLocationChanged(Location location)
    {
        finishLocationUpdates(location);
    }

    public void finishLocationUpdates(Location location)
    {

        stopLocationUpdates();
        dismissGettingLocationAlert();

        String mapLink = String.format(Locale.ENGLISH, "http://maps.google.com/maps?q=%f,%f",
                                       location.getLatitude(), location.getLongitude());

        CircleOf6Application
                .defaultLabelTrackingEvent(this, ConstantsAnalytics.ACTION_COME_AND_GET_ME);

        String smsBody = checkCustom(ConstantsAnalytics.ACTION_COME_AND_GET_ME, getString(R.string.left_button_message));

        final boolean mapUrlFront = getResources().getBoolean(R.bool.map_url_front);
        final String messageString = mapUrlFront

                ?
                (mapLink + " " + smsBody + " " + getString(
                        R.string.message_footer_short))

                :
        (smsBody + " " + mapLink + " " + getString(
                R.string.message_footer_short))
                ;

        sendSMSWithBody(messageString);
    }

    private void startLocationUpdates()
    {
        if (!askForContactPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            mRequestingLocationUpdates = true;
            LocationServices.FusedLocationApi
                    .requestLocationUpdates(mGoogleApiClient, makeLocationRequest(), this);
        }
    }

    protected LocationRequest makeLocationRequest()
    {
        LocationRequest mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(0);
        mLocationRequest.setFastestInterval(0);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        return mLocationRequest;
    }

    @Override
    public void onConnected(Bundle bundle)
    {
        if(mRequestingLocationUpdates)
        {
            startLocationUpdates();
        }
    }

    @Override
    public void onConnectionSuspended(int i)
    {
        if(mGoogleApiClient !=null )
            mGoogleApiClient.connect();
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult)
    {

        if(mRequestingLocationUpdates)
        {
            dismissGettingLocationAlert();
            tryResolvedConnectionGPS(connectionResult);
            mRequestingLocationUpdates = false;
        }
    }

    protected void stopLocationUpdates()
    {
        mRequestingLocationUpdates = false;
        if (mGoogleApiClient !=null) {
            if (mGoogleApiClient.isConnected()) {
                LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
            }
        }
    }

    private void disconnectGoogleApiClient()
    {
        if(mGoogleApiClient !=null) {

            if (!mRequestingLocationUpdates && mGoogleApiClient.isConnected()) {
                mGoogleApiClient.disconnect();
            }
        }
    }

    //----------------------------------------------------------------OTHER
    private Calendar geDateShareNotification()
    {
        Calendar calendar = Calendar.getInstance();

        //Add days
        calendar.add(Calendar.DAY_OF_MONTH, Constants.DAYS_AFTER_NOTIFICATION_SHARE);

        //Set time of day
        calendar.set(Calendar.HOUR_OF_DAY, Constants.HOUR_NOTIFICATION_SHARE);
        calendar.set(Calendar.MINUTE, Constants.MINUTE_NOTIFICATION_SHARE);

        return calendar;
    }

    public void saveDateShareNotification(Calendar calendar)
    {
        AppPreferences.getInstance(this)
                      .saveDateNotificationShare(calendar.get(Calendar.DAY_OF_MONTH),
                                                 calendar.get(Calendar.MONTH),
                                                 calendar.get(Calendar.YEAR));
    }

    //Todo Optimizar Contactos a futuro
    public void updateCircle(int numContact, Intent data)
    {

        if(data != null)
        {
            Uri uri = data.getData();
            if(uri != null)
            {
                Cursor contactCursor;
                String id = uri.getLastPathSegment();
                contactCursor = getContentResolver()
                        .query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                               Constants.CONTACT_DATA_FIELDS, Constants.CONTACT_ID_SELECTION,
                               new String[] {id}, null);

                obtainContact(numContact, contactCursor);
                if(contactCursor != null)
                {
                    contactCursor.close();
                }
            }
        }
    }

    private void obtainContact(int numContact, Cursor contactCursor)
    {
        if(contactCursor != null && contactCursor.moveToFirst())
        {

            String name = shortenName(contactCursor.getString(1));
            String photoId = contactCursor.getString(2);

            if(contactCursor.getCount() > 1)
            {
                showDialogContact(numContact, contactCursor, name, photoId);
            }
            else
            {
                String number = contactCursor.getString(0);
                updateContact(numContact, name, number, photoId);
            }
        }
        else
        {
            Toast.makeText(this, R.string.message_friend_without_phone, Toast.LENGTH_SHORT).show();
        }
    }

    private String shortenName(String preName)
    {

        if(! TextUtils.isEmpty(preName))
        {
            preName = preName.split("\\s+")[0];
            if(preName.length() > Constants.nameMaxChars)
            {
                return preName.substring(0, Constants.nameMaxChars - 3) + "...";
            }
            else
            {
                return preName;
            }
        }
        else
        {
            return "";
        }

    }

    private void showDialogContact(final int reqCode, Cursor contactCursor, final String name, final String photoId)
    {
        ContactNumbersDialog numbersDialog = ContactNumbersDialog
                .getInstance(getNumbersOfContact(contactCursor));

        numbersDialog.setOnSelectContactListener(new ContactNumbersDialog.OnSelectContactListener()
        {
            @Override
            public void onSelectPhone(String phone)
            {
                updateContact(reqCode, name, phone, photoId);
            }
        });

        numbersDialog.show(getSupportFragmentManager(), "");
    }

    private ArrayList<String> getNumbersOfContact(Cursor contactCursor)
    {
        ArrayList<String> numbersPhones = new ArrayList<>();
        do
        {
            String number = contactCursor.getString(0);
            numbersPhones.add(number);
        }
        while(contactCursor.moveToNext());

        return numbersPhones;
    }

    private void updateContact(int contactId, String name, String phoneNumber, String photoId)
    {

        int circleSizePreUpdate = contactsPicked.size();

        Contact updatedContact = saveContact(contactId, name, phoneNumber, photoId);

        circleOf6View.addContact(updatedContact, contactId - 1);

        if(! updatedContact.isEmpty())
        {
            contactsPicked.add(updatedContact.getId());
        }

        int circleSizePostUpdate = contactsPicked.size();

        checkIfCircleNewlyComplete(circleSizePreUpdate, circleSizePostUpdate);

        showDialogsSendSmsNewContact(name, phoneNumber, circleSizePreUpdate, circleSizePostUpdate);


    }

    private Contact saveContact(int contactId, String name, String phoneNumber, String photoId)
    {

        String photo;

        AppPreferences.getInstance(this).saveNameCotact(contactId, name);
        AppPreferences.getInstance(this).savePhoneCotact(contactId, phoneNumber);


        if(writeContactPhotoBitmap(photoId, contactId))
        {
            File filePath = getFileStreamPath(getPhotoFileByContact(contactId));
            photo = filePath.toString();
            AppPreferences.getInstance(this).savaPhotoCotact(contactId, photo);
        }
        else
        {
            photo = Constants.PHOTO_IS_DEFAULT;
            AppPreferences.getInstance(this).savaPhotoCotact(contactId, photo);
        }

        return new Contact(contactId, name, phoneNumber, photo);
    }

    public void checkIfCircleNewlyComplete(int preUp, int postUp)
    {
        if(preUp < 3 && postUp > 2)
        {
            CircleOf6Application
                    .defaultLabelTrackingEvent(this, ConstantsAnalytics.ACTION_3_CONTACTS_ADDED);
            enableCircleAction();
        }
        else if(preUp < 6 && postUp == 6)
        {
            CircleOf6Application
                    .defaultLabelTrackingEvent(this, ConstantsAnalytics.ACTION_6_CONTACTS_ADDED);
            showCircleCompleteDialog();
        }
    }

    public void showDialogsSendSmsNewContact(final String name, final String phone, int preUp, int postUp)
    {

        if((preUp == 0 && postUp == 1) || (preUp == 6 && postUp >= 6))
        {
            final OverlayDialog dialog = OverlayDialog.createDialog(getString(R.string.alert_alert_your_friend));
            dialog
                    .setPressOkSendSmsListener(new OverlayDialog.PressOkSendSmsListener()
                    {
                        @Override
                        public void onPressOkSendSms()
                        {
                            sendSMSNewContact(name, phone,
                                              getString(R.string.circle_complete_confirmation));
                        }
                    });
            dialog
                    .show(getSupportFragmentManager(), "dialog_send_sme_new_contact");
        }
        else
        {

            sendSMSNewContact(name, phone, getString(R.string.circle_complete_confirmation));
        }

    }

    private void showCircleCompleteDialog()
    {
        final OverlayDialog dialog = OverlayDialog.createDialog(getString(R.string.alert_circle_complete));
        dialog.show(getSupportFragmentManager(), "dialog_complete_circle");
    }

    public boolean writeContactPhotoBitmap(String photoId, int friendId)
    {

        if(photoId == null)
        {
            return false;
        }
        byte[] photo = null;
        Uri photoUri = ContentUris
                .withAppendedId(ContactsContract.Data.CONTENT_URI, Long.parseLong(photoId));
        Cursor c = getContentResolver()
                .query(photoUri, new String[] {ContactsContract.CommonDataKinds.Photo.PHOTO}, null,
                       null, null);

        if(c != null)
        {
            if(c.moveToFirst())
            {
                photo = c.getBlob(0);
            }
            c.close();
        }

        if(photo != null)
        {
            Bitmap contactPhoto = BitmapFactory.decodeStream(new ByteArrayInputStream(photo));

            if(contactPhoto.getWidth() > 0 && contactPhoto.getHeight() > 0)
            {
                // scale the contact photo and maks
                String photoPng = getPhotoFileByContact(friendId);
                FileOutputStream fos;
                try
                {
                    fos = openFileOutput(photoPng, Context.MODE_PRIVATE);

                    Bitmap contactPhotoCirculate = DrawUtils
                            .getCroppedAndMaskedBitmap(circleOf6View, contactPhoto);

                    contactPhotoCirculate.compress(CompressFormat.PNG, 100, fos);
                    fos.close();
                    return true;

                }
                catch(FileNotFoundException e)
                {
                    e.printStackTrace();
                    return false;

                }
                catch(IOException e)
                {
                    e.printStackTrace();
                    return false;
                }
            }

        }
        return false; // did not succeed

    }

    public void showAlertSentMessageSuccess()
    {
        numMessagesSent++;
        if(numMessagesSent == numSmsSent)
        {
            if(! isStopActivity)
            {
                showSuccessDialog();
            }
            else
            {
                showAlertSentMessageSuccess = true;
            }
        }
    }

    public void showSuccessDialog()
    {
        final OverlayDialog dialog = OverlayDialog.createDialog(getString(R.string.alert_request_sent));
        dialog.show(getSupportFragmentManager(), "dialog_success_send_dialog");
    }

    private final int PERMISSION_REQUEST_CONTACT = 999;

    public boolean askForContactPermission(final String manifestPermission){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this,manifestPermission) != PackageManager.PERMISSION_GRANTED) {

                // Should we show an explanation?
                if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                        manifestPermission)) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Permission needed");
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.setMessage("please confirm permissions");//TODO put real question
                    builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @TargetApi(Build.VERSION_CODES.M)
                        @Override
                        public void onDismiss(DialogInterface dialog) {
                            requestPermissions(
                                    new String[]
                                            {manifestPermission}
                                    , PERMISSION_REQUEST_CONTACT);
                        }
                    });
                    builder.show();
                    // Show an expanation to the user *asynchronously* -- don't block
                    // this thread waiting for the user's response! After the user
                    // sees the explanation, try again to request the permission.

                } else {

                    // No explanation needed, we can request the permission.

                    ActivityCompat.requestPermissions(this,
                            new String[]{manifestPermission},
                            PERMISSION_REQUEST_CONTACT);

                    // MY_PERMISSIONS_REQUEST_READ_CONTACTS is an
                    // app-defined int constant. The callback method gets the
                    // result of the request.
                }

                return true;
            }else{
              //  getContact();
                return false;
            }
        }
        else{
           // getContact();
            return false;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_REQUEST_CONTACT: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                   // getContact();
                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                        askForContactPermission(Manifest.permission.SEND_SMS);

                    } else {
                    Toast.makeText(this, "No Permissions ", Toast.LENGTH_SHORT).show();
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }

            // other 'case' lines to check for other
            // permissions this app might request
        }
    }
}
