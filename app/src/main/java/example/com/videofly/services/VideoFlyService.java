package example.com.videofly.services;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;

import com.opentok.android.BaseVideoRenderer;
import com.opentok.android.OpentokError;
import com.opentok.android.Publisher;
import com.opentok.android.PublisherKit;
import com.opentok.android.Session;
import com.opentok.android.Stream;
import com.opentok.android.Subscriber;
import com.opentok.android.SubscriberKit;

import org.json.JSONObject;

import java.util.ArrayList;

import example.com.videofly.CircleVideoRenderer;
import example.com.videofly.MainActivity;
import example.com.videofly.R;
import example.com.videofly.VideoCallActivity;

/**
 * Created by madhavchhura on 5/12/15.
 */
public class VideoFlyService  extends Service implements
        Session.SessionListener, Publisher.PublisherListener,
        Subscriber.VideoListener{

    private static final String LOGTAG = "VideoFlyService";
    private WindowManager windowManager;

    // Spinning wheel for loading subscriber view
    private ProgressBar mLoadingSub;

    //RelativeLayouts
    private RelativeLayout videoFlyHead; //Root Layout
    private RelativeLayout mSubscriberViewContainer; //SubscriberLayout
    private LinearLayout mViewGroupLayout;
    private ImageButton endCallButton;

    //Variables for the OpenTok Methods
    private Session mSession;
    private Publisher mPublisher;
    private Subscriber mSubscriber;
    private ArrayList<Stream> mStreams;

    public static final String VIDEO_FLY_SERVICE = "VideoFlyService";

    @Override
    public IBinder onBind(Intent arg0) {
        return null;
    }

    @Override public void onCreate() {
        super.onCreate();

        Log.d(LOGTAG, "VideoHeadService - onCreate");

        endCallButton = new ImageButton(getApplicationContext());
        videoFlyHead = new RelativeLayout(getApplicationContext());
        mStreams = new ArrayList<Stream>();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);


        videoFlyHead = (RelativeLayout) LayoutInflater.from(this).
                inflate(R.layout.activity_video_fly, null, true);

        mLoadingSub = (ProgressBar) videoFlyHead.findViewById(R.id.loadingSpinner);
        mSubscriberViewContainer = (RelativeLayout) videoFlyHead.findViewById(R.id.subscriber);

        mViewGroupLayout = (LinearLayout) LayoutInflater.from(this).
                inflate(R.layout.activity_video_controls, null);
        videoFlyHead.addView(mViewGroupLayout);

        endCallButton = (ImageButton) mViewGroupLayout.findViewById(R.id.button_end_call);
        endCallButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                endCallButtonClicked(v);
            }
        });


        JSONObject json = null;

        Log.d("VideoCallActivity", "Created using MainActivity sessionID and token ID");
        sessionConnect(MainActivity.sessionId, MainActivity.token);

        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        |WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);

        videoFlyHead.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;

            //Used to make the Head movable.
            @Override public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_UP:
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(videoFlyHead, params);
                        return true;
                }
                return false;
            }
        });
        windowManager.addView(videoFlyHead, params);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if(mStreams != null){
            mStreams.clear();
        }
        this.mSubscriber = null;
        if (mSession != null) {
            mSession.disconnect();
        }
        if (videoFlyHead != null)
            windowManager.removeView(videoFlyHead);
    }
    //Session Methods
    private void sessionConnect(String sessionId,String token) {
        Log.d(LOGTAG, "in sessionConnect");
        if (mSession == null) {
            mSession = new Session(VideoFlyService.this,
                    getResources().getString(R.string.opentok_api_key),
                    sessionId);
            mSession.setSessionListener(this);
            mSession.connect(token);

            Log.d(LOGTAG, "leaving sessionConnect if loop");
        }
        Log.d(LOGTAG, "leaving sessionConnect");
    }

    @Override
    public void onConnected(Session session) {
        Log.i(LOGTAG, "Connected to the session.");
        if (mPublisher == null) {
            mPublisher = new Publisher(VideoFlyService.this, "publisher");
            mPublisher.setPublisherListener(this);
            //attachPublisherView(mPublisher);
            mSession.publish(mPublisher);
        }
        Log.i(LOGTAG, "Connected to the session.");
    }

    @Override
    public void onDisconnected(Session session) {
        Log.i(LOGTAG, "Disconnected from the session.");
        if (mPublisher != null) {
            //mPublisherViewContainer.removeView(mPublisher.getView());
        }

        if (mSubscriber != null) {
            mSubscriberViewContainer.removeView(mSubscriber.getView());
        }

        mPublisher = null;
        mSubscriber = null;
        mStreams.clear();
        mSession = null;
    }

    @Override
    public void onStreamReceived(Session session, Stream stream) {
        if(!MainActivity.subscribeToSelf) {
            mStreams.add(stream);
            if (mSubscriber == null) {
                subscribeToStream(stream);
            } else {
                mSession.subscribe(mSubscriber);
            }
        }
    }

    @Override
    public void onStreamDropped(Session session, Stream stream) {
        if(!MainActivity.subscribeToSelf) {
            if ((mSubscriber != null)) {
                unsubscribeFromStream(stream);
            }
        }

    }

    @Override
    public void onError(Session session, OpentokError opentokError) {
        Log.i(LOGTAG, "Publisher exception: " + opentokError.getMessage());
    }

    //Publisher.PublisherListener
    @Override
    public void onStreamCreated(PublisherKit publisherKit, Stream stream) {
        if (MainActivity.subscribeToSelf) {
            mStreams.add(stream);
            if (mSubscriber == null) {
                subscribeToStream(stream);
            }
        }
    }

    @Override
    public void onStreamDestroyed(PublisherKit publisherKit, Stream stream) {
        if(!MainActivity.subscribeToSelf && mSubscriber != null)
            unsubscribeFromStream(stream);
    }

    @Override
    public void onError(PublisherKit publisherKit, OpentokError opentokError) {
        Log.i(LOGTAG, "Publisher exception: " + opentokError.getMessage());
    }


    //Subscriber.VideoListener
    @Override
    public void onVideoDataReceived(SubscriberKit subscriberKit) {
        Log.i(LOGTAG, "First frame received");

        // stop loading spinning
        mLoadingSub.setVisibility(View.GONE);
        attachSubscriberView(mSubscriber);
    }

    @Override
    public void onVideoDisabled(SubscriberKit subscriberKit, String s) {
        Log.i(LOGTAG, "Video disabled:" + s);
    }

    @Override
    public void onVideoEnabled(SubscriberKit subscriber, String reason) {
        Log.i(LOGTAG, "Video enabled:" + reason);
    }

    @Override
    public void onVideoDisableWarning(SubscriberKit subscriber) {
        Log.i(LOGTAG, "Video may be disabled soon due to network quality degradation. Add UI handling here.");
    }

    @Override
    public void onVideoDisableWarningLifted(SubscriberKit subscriber) {
        Log.i(LOGTAG, "Video may no longer be disabled as stream quality improved. Add UI handling here.");
    }


    private void subscribeToStream(Stream stream) {
        Log.d(LOGTAG, "subscribing to stream");
        mSubscriber = new Subscriber(this, stream);
        CircleVideoRenderer circleVideoRenderer = new CircleVideoRenderer(getApplicationContext());
        mSubscriber.setRenderer(circleVideoRenderer);
        mSubscriber.setVideoListener(this);
        mSubscriber.setSubscribeToAudio(true);
        mSubscriber.setSubscribeToVideo(true);
        mSession.subscribe(mSubscriber);

        if (mSubscriber.getSubscribeToVideo()) {
            // start loading spinning
            mLoadingSub.setVisibility(View.VISIBLE);
        }

        Log.d(LOGTAG, "leaving subscribing to stream");

    }

    private void unsubscribeFromStream(Stream stream) {
        mStreams.remove(stream);
        if (mSubscriber.getStream().getStreamId().equals(stream.getStreamId())) {
            mSubscriberViewContainer.removeView(mSubscriber.getView());
            mSubscriber = null;
            if (!mStreams.isEmpty()) {
                subscribeToStream(mStreams.get(0));
            }
        }
    }
    private void attachSubscriberView(Subscriber subscriber) {
        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(
                getResources().getDisplayMetrics().widthPixels, getResources()
                .getDisplayMetrics().heightPixels);
        mSubscriberViewContainer.removeView(mSubscriber.getView());
        mSubscriberViewContainer.addView(mSubscriber.getView(), layoutParams);
        subscriber.setStyle(BaseVideoRenderer.STYLE_VIDEO_SCALE,
                BaseVideoRenderer.STYLE_VIDEO_FILL);
    }

    public void endCallButtonClicked(View view){
        VideoCallActivity.mNotificationManager.cancel(ClearNotificationService.NOTIFICATION_ID);
        Intent i = new Intent(VideoFlyService.this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
        stopService(new Intent(getApplicationContext(), VideoFlyService.class));
    }
}