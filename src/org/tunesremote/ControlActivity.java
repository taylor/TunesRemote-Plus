/*
    TunesRemote+ - http://code.google.com/p/tunesremote-plus/
    
    Copyright (C) 2008 Jeffrey Sharkey, http://jsharkey.org/
    Copyright (C) 2010 TunesRemote+, http://code.google.com/p/tunesremote-plus/
    
    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.
    
    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.
    
    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
    
    The Initial Developer of the Original Code is Jeffrey Sharkey.
    Portions created by Jeffrey Sharkey are
    Copyright (C) 2008. Jeffrey Sharkey, http://jsharkey.org/
    All Rights Reserved.
 */

package org.tunesremote;

import java.util.List;

import org.tunesremote.daap.Response;
import org.tunesremote.daap.Session;
import org.tunesremote.daap.Speaker;
import org.tunesremote.daap.Status;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.AnimationUtils;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ImageSwitcher;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RatingBar;
import android.widget.RatingBar.OnRatingBarChangeListener;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewSwitcher.ViewFactory;

/**
 * Main activity of TunesRemote. This controls the player and drives all the other activities.
 * <p>
 */
public class ControlActivity extends Activity implements ViewFactory {

   public final static String TAG = ControlActivity.class.toString();

   /**
    * ID of the speakers dialog
    */
   private static final int DIALOG_SPEAKERS = 1;

   protected static BackendService backend;
   protected static Session session;
   protected static Status status;
   protected boolean dragging = false;
   protected String showingAlbumId = null;

   public ServiceConnection connection = new ServiceConnection() {
      public void onServiceConnected(ComponentName className, IBinder service) {
         try {
            backend = ((BackendService.BackendBinder) service).getService();
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(ControlActivity.this);
            backend.setPrefs(settings);

            if (!agreed)
               return;

            Log.w(TAG, "onServiceConnected");

            session = backend.getSession();
            if (session == null) {
               // we could not connect with library, so launch picker
               ControlActivity.this.startActivityForResult(new Intent(ControlActivity.this, LibraryActivity.class), 1);

            } else {
               // for some reason we are not correctly disposing of the session
               // threads we create so we purge any existing ones before creating a
               // new one
               status = session.singletonStatus(statusUpdate);
               status.updateHandler(statusUpdate);

               // push update through to make sure we get updated
               statusUpdate.sendEmptyMessage(Status.UPDATE_TRACK);
            }
         } catch (Exception e) {
            Log.e(TAG, "onServiceConnected:" + e.getMessage());
         }

      }

      public void onServiceDisconnected(ComponentName className) {
         // make sure we clean up our handler-specific status
         Log.w(TAG, "onServiceDisconnected");

         status.updateHandler(null);

         backend = null;
         status = null;
      }
   };

   protected Handler statusUpdate = new Handler() {

      @Override
      public void handleMessage(Message msg) {
         // update gui based on severity
         switch (msg.what) {
         case Status.UPDATE_TRACK:
            trackName.setText(status.getTrackName());
            trackArtist.setText(status.getTrackArtist());
            trackAlbum.setText(status.getTrackAlbum());

            // Set to invisible until rating is known
            ratingBar.setVisibility(RatingBar.INVISIBLE);

            // fade new details up if requested
            if (fadeUpNew)
               fadeview.keepAwake();

         case Status.UPDATE_COVER:

            boolean forced = (msg.what == Status.UPDATE_COVER);
            boolean shouldUpdate = (status.albumId != showingAlbumId) && !status.coverEmpty;
            if (forced)
               shouldUpdate = true;

            // only update coverart if different than already shown
            Log.d(TAG, String.format("Artwork for albumid=%s, value=%s, what=%d", status.albumId, status.coverCache, msg.what));
            if (shouldUpdate) {
               if (status.coverEmpty) {
                  // fade down if no coverart
                  cover.setImageDrawable(new ColorDrawable(Color.BLACK));
               } else if (status.coverCache != null) {
                  // fade over to new coverart
                  cover.setImageDrawable(new BitmapDrawable(status.coverCache));
               }
               showingAlbumId = status.albumId;
            }
         case Status.UPDATE_STATE:
            controlPause.setImageResource((status.getPlayStatus() == Status.STATE_PLAYING) ? android.R.drawable.ic_media_pause
                     : android.R.drawable.ic_media_play);
            seekBar.setMax(status.getProgressTotal());

         case Status.UPDATE_PROGRESS:
            if (ignoreNextTick) {
               ignoreNextTick = false;
               return;
            }
            seekPosition.setText(Response.convertTime(status.getProgress() * 1000));
            seekRemain.setText("-" + Response.convertTime(status.getRemaining() * 1000));
            if (!dragging) {
               seekBar.setProgress(status.getProgress());
            }
            break;

         // This one is triggered by a thread, so should not be used to update
         // progress, etc...
         case Status.UPDATE_RATING:
            long rating = status.getRating();
            if (rating >= 0) {
               ratingBar.setRating(((float) status.getRating() / 100) * 5);
               ratingBar.setVisibility(RatingBar.VISIBLE);

               // fade new details up if requested
               if (fadeUpNew)
                  fadeview.keepAwake();
            }
            break;
         }
      }
   };

   public android.telephony.PhoneStateListener psListener = new android.telephony.PhoneStateListener() {
      private boolean wasPlaying = false;

      @Override
      public void onCallStateChanged(int state, java.lang.String incomingNumber) {

         if (ControlActivity.this.autoPause) {
            switch (state) {
            case android.telephony.TelephonyManager.CALL_STATE_IDLE:
               if (wasPlaying && session != null && ControlActivity.status.getPlayStatus() == Status.STATE_PAUSED) {
                  session.controlPlayPause();
                  wasPlaying = false;
               }
               break;
            case android.telephony.TelephonyManager.CALL_STATE_OFFHOOK:
               if (session != null && ControlActivity.status.getPlayStatus() == Status.STATE_PLAYING) {
                  session.controlPlayPause();
                  wasPlaying = true;
               }
               break;
            case android.telephony.TelephonyManager.CALL_STATE_RINGING:
               if (session != null && ControlActivity.status.getPlayStatus() == Status.STATE_PLAYING) {
                  session.controlPlayPause();
                  wasPlaying = true;
               }
               break;
            default:
               break;
            }
         }
      }

   };

   protected void StartNowPlaying() {
      if (status == null)
         return;

      // launch tracks view for current album
      Intent intent = new Intent(ControlActivity.this, NowPlayingActivity.class);
      intent.putExtra(Intent.EXTRA_TITLE, status.getAlbumId());
      ControlActivity.this.startActivity(intent);
   }

   protected Handler doubleTapHandler = new Handler() {

      @Override
      public void handleMessage(Message msg) {
         StartNowPlaying();
      }
   };

   protected RatingBar ratingBar;
   protected TextView trackName, trackArtist, trackAlbum, seekPosition, seekRemain;
   protected SeekBar seekBar;
   protected ImageSwitcher cover;
   protected ImageButton controlPrev, controlPause, controlNext;

   public View makeView() {
      ImageView view = new ImageView(this);
      view.setScaleType(ImageView.ScaleType.CENTER_CROP);
      view.setLayoutParams(new ImageSwitcher.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));
      return view;
   }

   protected View volume;
   protected ProgressBar volumeBar;
   protected Toast volumeToast;
   protected FadeView fadeview;
   protected Toast shuffleToast;
   protected Toast repeatToast;

   protected boolean stayConnected = false, fadeDetails = true, fadeUpNew = true, vibrate = true;
   protected boolean autoPause = false;

   @Override
   public void onStart() {
      super.onStart();

      this.stayConnected = this.prefs.getBoolean(this.getString(R.string.pref_background), this.stayConnected);
      this.fadeDetails = this.prefs.getBoolean(this.getString(R.string.pref_fade), this.fadeDetails);
      this.fadeUpNew = this.prefs.getBoolean(this.getString(R.string.pref_fadeupnew), this.fadeUpNew);
      this.vibrate = this.prefs.getBoolean(this.getString(R.string.pref_vibrate), this.vibrate);
      this.autoPause = this.prefs.getBoolean(this.getString(R.string.pref_autopause), this.autoPause);

      this.fadeview.allowFade = this.fadeDetails;
      this.fadeview.keepAwake();

      Intent service = new Intent(this, BackendService.class);

      if (this.stayConnected) {
         // if were running background service, start now
         this.startService(service);

      } else {
         // otherwise make sure we kill the static background service
         this.stopService(service);

      }

      // regardless of stayConnected, were always going to need a bound backend
      // for this activity
      this.bindService(service, connection, Context.BIND_AUTO_CREATE);

      android.telephony.TelephonyManager tm = (android.telephony.TelephonyManager) ControlActivity.this
               .getSystemService(android.content.Context.TELEPHONY_SERVICE);
      tm.listen(psListener, android.telephony.PhoneStateListener.LISTEN_CALL_STATE);
   }

   @Override
   public void onStop() {
      super.onStop();
      Log.w(TAG, "Stopping TunesRemote...");
      try {
         if (!this.stayConnected && session != null) {
            session.purgeAllStatus();
         }

         this.unbindService(connection);
      } catch (Exception ex) {
         Log.e(TAG, ex.getMessage(), ex);
      }
   }

   @Override
   protected void onDestroy() {
      super.onDestroy();
      Log.w(TAG, "Destroying TunesRemote...");
      try {
         if (session != null) {
            session.purgeAllStatus();
            session.logout();
            session = null;
         }
         backend = null;
      } catch (Exception ex) {
         Log.e(TAG, ex.getMessage(), ex);
      }
      Log.w(TAG, "Destroyed TunesRemote!");
   }

   protected Vibrator vibrator;
   protected SharedPreferences prefs;
   protected boolean agreed = false;

   public final static String EULA = "eula";

   @Override
   protected void onActivityResult(int requestCode, int resultCode, Intent data) {

      if (resultCode == Activity.RESULT_OK) {
         // yay they agreed, so store that info
         Editor edit = prefs.edit();
         edit.putBoolean(EULA, true);
         edit.commit();
         this.agreed = true;
      } else {
         // user didnt agree, so close
         this.finish();
      }

   }

   public final static int VIBRATE_LEN = 150;
   protected boolean ignoreNextTick = false;

   // public boolean shouldPause = false;

   /**
    * List of available speakers
    */
   protected List<Speaker> speakers;

   /**
    * Instance of the speaker list adapter used in the speakers dialog
    */
   protected SpeakersAdapter speakersAdapter;

   /**
    * OnSeekBarChangeListener that controls the volume for a certain speaker
    * @author Daniel Thommes
    */
   public class VolumeSeekBarListener implements OnSeekBarChangeListener {
      private final Speaker speaker;

      public VolumeSeekBarListener(Speaker speaker) {
         this.speaker = speaker;
      }

      public void onStopTrackingTouch(SeekBar seekBar) {
      }

      public void onStartTrackingTouch(SeekBar seekBar) {
      }

      public void onProgressChanged(SeekBar seekBar, int newVolume, boolean fromUser) {
         if (fromUser) {
            // Volume of the loudest speaker
            int maxVolume = 0;
            // Volume of the second loudest speaker
            int secondMaxVolume = 0;
            for (Speaker speaker : speakers) {
               if (speaker.getAbsoluteVolume() > maxVolume) {
                  secondMaxVolume = maxVolume;
                  maxVolume = speaker.getAbsoluteVolume();
               } else if (speaker.getAbsoluteVolume() > secondMaxVolume) {
                  secondMaxVolume = speaker.getAbsoluteVolume();
               }
            }
            // fetch the master volume if necessary
            checkCachedVolume();
            int formerVolume = speaker.getAbsoluteVolume();
            status.setSpeakerVolume(speaker.getId(), newVolume, formerVolume, maxVolume, secondMaxVolume, cachedVolume);
            speaker.setAbsoluteVolume(newVolume);
         }
      }
   }

   /**
    * List Adapter for displaying the list of available speakers.
    * @author Daniel Thommes
    */
   public class SpeakersAdapter extends BaseAdapter {

      private final LayoutInflater inflater;

      public SpeakersAdapter(Context context) {
         inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
      }

      public int getCount() {
         return speakers.size();
      }

      public Object getItem(int position) {
         return speakers.get(position);
      }

      public long getItemId(int position) {
         return position;
      }

      /**
       * Toggles activation of a given speaker and refreshes the view
       * @param active Flag indicating, whether the speaker shall be activated
       * @param speaker the speaker to be activated or deactivated
       */
      public void setSpeakerActive(boolean active, final Speaker speaker) {
         speaker.setActive(active);
         status.setSpeakers(speakers);
         speakers = status.getSpeakers();
         notifyDataSetChanged();
      }

      public View getView(int position, View convertView, ViewGroup parent) {
         try {

            View row;
            if (null == convertView) {
               row = inflater.inflate(R.layout.item_speaker, null);
            } else {
               row = convertView;
            }

            /*************************************************************
             * Find the necessary sub views
             *************************************************************/
            TextView nameTextview = (TextView) row.findViewById(R.id.speakerNameTextView);
            TextView speakerTypeTextView = (TextView) row.findViewById(R.id.speakerTypeTextView);
            final CheckBox activeCheckBox = (CheckBox) row.findViewById(R.id.speakerActiveCheckBox);
            SeekBar volumeBar = (SeekBar) row.findViewById(R.id.speakerVolumeBar);

            /*************************************************************
             * Set view properties
             *************************************************************/
            final Speaker speaker = speakers.get(position);
            nameTextview.setText(speaker.getName());
            speakerTypeTextView.setText(speaker.isLocalSpeaker() ? R.string.speakers_dialog_computer_speaker : R.string.speakers_dialog_airport_express);
            activeCheckBox.setChecked(speaker.isActive());
            activeCheckBox.setOnClickListener(new OnClickListener() {

               public void onClick(View v) {
                  setSpeakerActive(activeCheckBox.isChecked(), speaker);
               }
            });
            nameTextview.setOnClickListener(new OnClickListener() {

               public void onClick(View v) {
                  activeCheckBox.toggle();
                  setSpeakerActive(activeCheckBox.isChecked(), speaker);
               }
            });
            speakerTypeTextView.setOnClickListener(new OnClickListener() {

               public void onClick(View v) {
                  activeCheckBox.toggle();
                  setSpeakerActive(activeCheckBox.isChecked(), speaker);
               }
            });
            // If the speaker is active, enable the volume bar
            if (speaker.isActive()) {
               volumeBar.setEnabled(true);
               volumeBar.setProgress(speaker.getAbsoluteVolume());
               volumeBar.setOnSeekBarChangeListener(new VolumeSeekBarListener(speaker));
            } else {
               volumeBar.setEnabled(false);
               volumeBar.setProgress(0);
            }
            return row;
         } catch (RuntimeException e) {
            Log.e(TAG, "Error when rendering speaker item: ", e);
            throw e;
         }
      }
   }

   /**
    * {@inheritDoc}
    * @see android.app.Activity#onCreateDialog(int)
    */

   @Override
   protected Dialog onCreateDialog(int id) {
      if (id == DIALOG_SPEAKERS) {
         // Create the speakers dialog (once)
         return new AlertDialog.Builder(this).setIcon(android.R.drawable.ic_lock_silent_mode_off).setTitle(R.string.control_menu_speakers)
                  .setAdapter(speakersAdapter, null).setPositiveButton("OK", null).create();
      }
      return null;
   }

   @Override
   public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);

      // before we go any further, make sure theyve agreed to EULA
      this.prefs = PreferenceManager.getDefaultSharedPreferences(this);
      this.agreed = prefs.getBoolean(EULA, false);
      if (!this.agreed) {
         // show eula wizard
         this.startActivityForResult(new Intent(this, WizardActivity.class), 1);

      }

      setContentView(R.layout.act_control);

      this.vibrator = (Vibrator) this.getSystemService(Context.VIBRATOR_SERVICE);

      // prepare volume toast view
      LayoutInflater inflater = (LayoutInflater) this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

      this.volume = inflater.inflate(R.layout.toa_volume, null, false);

      this.volumeBar = (ProgressBar) this.volume.findViewById(R.id.volume);

      this.volumeToast = new Toast(this);
      this.volumeToast.setDuration(Toast.LENGTH_SHORT);
      this.volumeToast.setGravity(Gravity.CENTER, 0, 0);
      this.volumeToast.setView(this.volume);

      this.shuffleToast = Toast.makeText(this, R.string.control_menu_shuffle_off, Toast.LENGTH_SHORT);
      this.shuffleToast.setGravity(Gravity.CENTER, 0, 0);

      this.repeatToast = Toast.makeText(this, R.string.control_menu_repeat_none, Toast.LENGTH_SHORT);
      this.repeatToast.setGravity(Gravity.CENTER, 0, 0);

      // pull out interesting controls
      this.trackName = (TextView) findViewById(R.id.info_title);
      this.trackArtist = (TextView) findViewById(R.id.info_artist);
      this.trackAlbum = (TextView) findViewById(R.id.info_album);
      this.ratingBar = (RatingBar) findViewById(R.id.rating_bar);

      this.cover = (ImageSwitcher) findViewById(R.id.cover);
      this.cover.setFactory(this);
      this.cover.setInAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_in));
      this.cover.setOutAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_out));

      this.seekBar = (SeekBar) findViewById(R.id.seek);
      this.seekPosition = (TextView) findViewById(R.id.seek_position);
      this.seekRemain = (TextView) findViewById(R.id.seek_remain);

      this.controlPrev = (ImageButton) findViewById(R.id.control_prev);
      this.controlPause = (ImageButton) findViewById(R.id.control_pause);
      this.controlNext = (ImageButton) findViewById(R.id.control_next);

      this.seekBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
         public void onProgressChanged(SeekBar seekBar, int progress, boolean fromTouch) {
         }

         public void onStartTrackingTouch(SeekBar seekBar) {
            dragging = true;
         }

         public void onStopTrackingTouch(SeekBar seekBar) {
            // scan to location in song
            dragging = false;
            session.controlProgress(seekBar.getProgress());
            ignoreNextTick = true;
            if (vibrate)
               vibrator.vibrate(VIBRATE_LEN);
         }
      });

      this.controlPrev.setOnClickListener(new OnClickListener() {
         public void onClick(View v) {
            session.controlPrev();
            if (vibrate)
               vibrator.vibrate(VIBRATE_LEN);
         }
      });

      this.controlNext.setOnClickListener(new OnClickListener() {
         public void onClick(View v) {
            session.controlNext();
            if (vibrate)
               vibrator.vibrate(VIBRATE_LEN);
         }
      });

      this.controlPause.setOnClickListener(new OnClickListener() {
         public void onClick(View v) {
            session.controlPlayPause();
            if (vibrate)
               vibrator.vibrate(VIBRATE_LEN);
         }
      });

      this.fadeview = (FadeView) findViewById(R.id.fadeview);
      this.fadeview.startFade();
      this.fadeview.doubleTapHandler = this.doubleTapHandler;

      this.ratingBar.setOnRatingBarChangeListener(new OnRatingBarChangeListener() {

         public void onRatingChanged(RatingBar ratingBar, float rating, boolean fromUser) {
            if (fromUser && rating <= 5) {
               session.controlRating((long) ((rating / 5) * 100), status.getTrackId());
            }
         }
      });

      // Speakers adapter needed for the speakers dialog
      speakersAdapter = new SpeakersAdapter(this);
   }

   protected long cachedTime = -1;
   protected long cachedVolume = -1;

   // keep volume cache for 10 seconds
   public final static long CACHE_TIME = 10000;

   protected void incrementVolume(long increment) {

      checkCachedVolume();

      // increment the volume and send control signal off
      this.cachedVolume += increment;
      session.controlVolume(this.cachedVolume);

      // update our volume gui and show
      this.volumeBar.setProgress((int) this.cachedVolume);
      this.volume.invalidate();
      this.volumeToast.show();

   }

   /**
    * Updates the cachedVolume if necessary
    */
   protected void checkCachedVolume() {
      // try assuming a cached volume instead of requesting it each time
      if (System.currentTimeMillis() - cachedTime > CACHE_TIME) {
         this.cachedVolume = status.getVolume();
         this.cachedTime = System.currentTimeMillis();
      }
   }

   @Override
   public boolean onKeyDown(int keyCode, KeyEvent event) {
      int increment = 5;
      try {
         increment = Integer.parseInt(backend.getPrefs().getString(getResources().getString(R.string.pref_volumeincrement), "5"));
      } catch (Exception e) {
         Log.e(TAG, "Volume Increment Exception:" + e.getMessage());
      }
      // check for volume keys
      if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
         this.incrementVolume(+increment);
         return true;
      } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
         this.incrementVolume(-increment);
         return true;
      }

      // regardless of key, make sure we keep view alive
      this.fadeview.keepAwake();

      return super.onKeyDown(keyCode, event);
   }

   // get rid of volume rocker default sound effect

   @Override
   public boolean onKeyUp(int keycode, KeyEvent event) {
      switch (keycode) {
      case KeyEvent.KEYCODE_VOLUME_DOWN:
      case KeyEvent.KEYCODE_VOLUME_UP:
         break;
      default:
         return super.onKeyUp(keycode, event);
      }
      return true;
   }

   protected MenuItem repeat;
   protected MenuItem shuffle;
   protected MenuItem speakersMenuItem;

   @Override
   public boolean onCreateOptionsMenu(Menu menu) {
      super.onCreateOptionsMenu(menu);

      MenuItem search = menu.add(R.string.control_menu_search);
      search.setIcon(android.R.drawable.ic_menu_search);
      search.setOnMenuItemClickListener(new OnMenuItemClickListener() {
         public boolean onMenuItemClick(MenuItem item) {
            ControlActivity.this.onSearchRequested();
            return true;
         }
      });

      MenuItem artists = menu.add(R.string.control_menu_artists);
      artists.setIcon(R.drawable.ic_search_category_music_artist);
      Intent artistIntent = new Intent(ControlActivity.this, BrowseActivity.class);
      artistIntent.putExtra("windowType", BaseBrowseActivity.RESULT_SWITCH_TO_ARTISTS);
      artists.setIntent(artistIntent);

      MenuItem playlists = menu.add("Playlists");
      playlists.setIcon(R.drawable.ic_search_category_music_song);
      Intent playlistIntent = new Intent(ControlActivity.this, BrowseActivity.class);
      playlistIntent.putExtra("windowType", BaseBrowseActivity.RESULT_SWITCH_TO_PLAYLISTS);
      playlists.setIntent(playlistIntent);

      speakersMenuItem = menu.add(R.string.control_menu_speakers);
      // a speaker icon from the default resources
      speakersMenuItem.setIcon(android.R.drawable.ic_lock_silent_mode_off);
      speakersMenuItem.setOnMenuItemClickListener(new OnMenuItemClickListener() {
         public boolean onMenuItemClick(MenuItem item) {
            showDialog(DIALOG_SPEAKERS);
            return true;
         }
      });
      // Visibility will be determined in onPrepareOptionsMenu depending on the
      // number of available speakers
      speakersMenuItem.setVisible(false);

      this.repeat = menu.add("Repeat");
      this.repeat.setIcon(android.R.drawable.ic_menu_rotate);
      this.repeat.setOnMenuItemClickListener(new OnMenuItemClickListener() {
         public boolean onMenuItemClick(MenuItem item) {

            if (session == null || status == null)
               return true;

            // correctly rotate through states
            switch (status.getRepeat()) {
            case Status.REPEAT_ALL:
               session.controlRepeat(Status.REPEAT_OFF);
               repeatToast.setText(R.string.control_menu_repeat_none);
               break;
            case Status.REPEAT_OFF:
               session.controlRepeat(Status.REPEAT_SINGLE);
               repeatToast.setText(R.string.control_menu_repeat_one);
               break;
            case Status.REPEAT_SINGLE:
               session.controlRepeat(Status.REPEAT_ALL);
               repeatToast.setText(R.string.control_menu_repeat_all);
               break;
            }

            repeatToast.show();
            return true;
         }
      });

      this.shuffle = menu.add("Shuffle");
      this.shuffle.setIcon(R.drawable.ic_menu_shuffle);
      this.shuffle.setOnMenuItemClickListener(new OnMenuItemClickListener() {
         public boolean onMenuItemClick(MenuItem item) {

            if (session == null || status == null)
               return true;

            // correctly rotate through states
            switch (status.getShuffle()) {
            case Status.SHUFFLE_OFF:
               session.controlShuffle(Status.SHUFFLE_ON);
               shuffleToast.setText(R.string.control_menu_shuffle_off);
               break;
            case Status.SHUFFLE_ON:
               session.controlShuffle(Status.SHUFFLE_OFF);
               shuffleToast.setText(R.string.control_menu_shuffle_on);
               break;
            }

            shuffleToast.show();
            return true;
         }
      });

      MenuItem pickLibrary = menu.add(R.string.control_menu_addlibrary);
      pickLibrary.setIcon(android.R.drawable.ic_menu_add);
      pickLibrary.setOnMenuItemClickListener(new OnMenuItemClickListener() {
         public boolean onMenuItemClick(MenuItem item) {
            // launch off library picking
            ControlActivity.this.startActivity(new Intent(ControlActivity.this, LibraryActivity.class));
            return true;
         }
      });

      MenuItem addLibrary = menu.add(R.string.control_menu_picklibrary);
      addLibrary.setIcon(android.R.drawable.ic_menu_share);
      addLibrary.setOnMenuItemClickListener(new OnMenuItemClickListener() {
         public boolean onMenuItemClick(MenuItem item) {
            // launch off library picking
            ControlActivity.this.startActivity(new Intent(ControlActivity.this, ServerActivity.class));
            return true;
         }
      });

      MenuItem nowPlaying = menu.add(R.string.control_menu_nowplaying);
      nowPlaying.setOnMenuItemClickListener(new OnMenuItemClickListener() {
         public boolean onMenuItemClick(MenuItem item) {
            // launch now playing
            StartNowPlaying();
            return true;
         }
      });

      MenuItem settings = menu.add("Settings");
      settings.setIcon(android.R.drawable.ic_menu_preferences);
      settings.setIntent(new Intent(ControlActivity.this, PrefsActivity.class));

      MenuItem about = menu.add("About");
      settings.setIcon(android.R.drawable.ic_menu_help);
      about.setIntent(new Intent(ControlActivity.this, WizardActivity.class));

      return true;
   }

   @Override
   public boolean onPrepareOptionsMenu(Menu menu) {
      super.onPrepareOptionsMenu(menu);

      if (session == null || status == null)
         return true;

      switch (status.getRepeat()) {
      case Status.REPEAT_ALL:
         this.repeat.setTitle(R.string.control_menu_repeat_none);
         break;
      case Status.REPEAT_OFF:
         this.repeat.setTitle(R.string.control_menu_repeat_one);
         break;
      case Status.REPEAT_SINGLE:
         this.repeat.setTitle(R.string.control_menu_repeat_all);
         break;
      }

      switch (status.getShuffle()) {
      case Status.SHUFFLE_OFF:
         this.shuffle.setTitle(R.string.control_menu_shuffle_on);
         break;
      case Status.SHUFFLE_ON:
         this.shuffle.setTitle(R.string.control_menu_shuffle_off);
         break;
      }

      // Determine whether the speakers menu item shall be visible
      speakers = status.getSpeakers();
      if (speakers.size() > 1) {
         speakersMenuItem.setVisible(true);
      } else {
         speakersMenuItem.setVisible(false);
      }

      return true;
   }

}
