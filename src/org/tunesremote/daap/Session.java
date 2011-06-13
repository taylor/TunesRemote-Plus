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

package org.tunesremote.daap;

import java.util.LinkedList;
import java.util.List;

import org.tunesremote.util.ThreadExecutor;

import android.os.Handler;
import android.util.Log;

public class Session {
   public final static String TAG = Session.class.toString();

   private final String host;
   private Status singleton = null;
   public String sessionId;
   public long databaseId, musicId, libraryId;
   public String databasePersistentId;
   public final List<Playlist> playlists = new LinkedList<Playlist>();

   public Session(String host, String pairingGuid) throws Exception {
      // start a session with the itunes server
      this.host = host;

      // http://192.168.254.128:3689/login?pairing-guid=0x0000000000000001
      Log.d(TAG, String.format("trying login for host=%s and guid=%s", host, pairingGuid));
      Response login = RequestHelper.requestParsed(String.format("%s/login?pairing-guid=0x%s", this.getRequestBase(),
               pairingGuid), false);
      this.sessionId = login.getNested("mlog").getNumberString("mlid");
      Log.d(TAG, String.format("found session-id=%s", this.sessionId));

      // http://192.168.254.128:3689/databases?session-id=1301749047
      Response databases = RequestHelper.requestParsed(String.format("%s/databases?session-id=%s", this.getRequestBase(), this.sessionId), false);
      this.databaseId = databases.getNested("avdb").getNested("mlcl").getNested("mlit").getNumberLong("miid");
      this.databasePersistentId = databases.getNested("avdb").getNested("mlcl").getNested("mlit").getNumberHex("mper");
      Log.d(TAG, String.format("found database-id=%s", this.databaseId));

      // fetch playlists to find the overall magic "Music" playlist
      Response playlists = RequestHelper
               .requestParsed(
                        String.format(
                                 "%s/databases/%d/containers?session-id=%s&meta=dmap.itemname,dmap.itemcount,dmap.itemid,dmap.persistentid,daap.baseplaylist,com.apple.itunes.special-playlist,com.apple.itunes.smart-playlist,com.apple.itunes.saved-genius,dmap.parentcontainerid,dmap.editcommandssupported",
                                 this.getRequestBase(), this.databaseId, this.sessionId), false);

      for (Response resp : playlists.getNested("aply").getNested("mlcl").findArray("mlit")) {
         String name = resp.getString("minm");
         if (name.equals("Music")) {
            this.musicId = resp.getNumberLong("miid");
         } else {
            // get a list of playlists, filter out some non-music iTunes
            // playlists
            if (name.equals("Films") || name.equals("TV Programmes") || name.equals("iTunes U"))
            // Ignore
            {
            } else if (resp.getNumberLong("abpl") == 1) {
               this.libraryId = resp.getNumberLong("miid");
            } else {
               Log.d(TAG, String.format("found playlist=%s", name));
               this.playlists.add(new Playlist(resp.getNumberLong("miid"), name, resp.getNumberLong("mimc"), resp.getNumberHex("mper")));
            }
         }
      }
      Log.d(TAG, String.format("found music-id=%s", this.musicId));
   }

   private Status createStatus(Handler handler) {
      final Status stat = new Status(this, handler);
      stat.fetchUpdate();
      return stat;

   }

   public Status singletonStatus(Handler handler) {
      if (singleton == null || singleton.destroyThread.get())
         singleton = this.createStatus(handler);
      return singleton;
   }

   public String getRequestBase() {
      return String.format("http://%s:3689", host);
   }

   public void purgeAllStatus() {
      if (singleton != null) {
         singleton.destroy();
         singleton = null;
      }
   }

   protected void notifyStatus() {
      if (singleton != null) {
         singleton.fetchUpdate();
      }
   }

   // some control helper functions
   // these should also invalidate any status listeners

   protected void fireAction(final String url, final boolean notify) {
      ThreadExecutor.runTask(new Runnable() {
         public void run() {
            RequestHelper.attemptRequest(url);
            if (notify)
               notifyStatus();
         }
      });
   }

   /**
    * Logout method disconnects the session on the server. This is being a good DACP citizen that was not happening in
    * previous versions.
    */
   public void logout() {
      Log.w(TAG, String.format("Logging Out session-id=%s", this.sessionId));
      this.fireAction(String.format("%s/logout?session-id=%s", this.getRequestBase(), this.sessionId), false);
   }

   public void controlPlayPause() {
      // http://192.168.254.128:3689/ctrl-int/1/playpause?session-id=130883770
      this.fireAction(String.format("%s/ctrl-int/1/playpause?session-id=%s", this.getRequestBase(), this.sessionId), true);
   }

   public void controlNext() {
      // http://192.168.254.128:3689/ctrl-int/1/nextitem?session-id=130883770
      this.fireAction(String.format("%s/ctrl-int/1/nextitem?session-id=%s", this.getRequestBase(), this.sessionId), true);
   }

   public void controlPrev() {
      // http://192.168.254.128:3689/ctrl-int/1/previtem?session-id=130883770
      this.fireAction(String.format("%s/ctrl-int/1/previtem?session-id=%s", this.getRequestBase(), this.sessionId), true);
   }

   public void controlVolume(long volume) {
      // http://192.168.254.128:3689/ctrl-int/1/setproperty?dmcp.volume=100.000000&session-id=130883770
      this.fireAction(String.format("%s/ctrl-int/1/setproperty?dmcp.volume=%s&session-id=%s", this.getRequestBase(), volume, this.sessionId), false);
   }

   public void controlProgress(int progressSeconds) {
      // http://192.168.254.128:3689/ctrl-int/1/setproperty?dacp.playingtime=82784&session-id=130883770
      this.fireAction(
               String.format("%s/ctrl-int/1/setproperty?dacp.playingtime=%d&session-id=%s", this.getRequestBase(), progressSeconds * 1000, this.sessionId),
               true);
   }

   public void controlShuffle(int shuffleMode) {
      // /ctrl-int/1/setproperty?dacp.shufflestate=1&session-id=1873217009
      this.fireAction(String.format("%s/ctrl-int/1/setproperty?dacp.shufflestate=%d&session-id=%s", this.getRequestBase(), shuffleMode, this.sessionId), false);
      singleton.shuffleStatus = shuffleMode;
   }

   public void controlRepeat(int repeatMode) {
      // /ctrl-int/1/setproperty?dacp.repeatstate=2&session-id=1873217009
      // HTTP/1.1
      this.fireAction(String.format("%s/ctrl-int/1/setproperty?dacp.repeatstate=%d&session-id=%s", this.getRequestBase(), repeatMode, this.sessionId), false);
      singleton.repeatStatus = repeatMode;
   }

   /**
    * Sets the rating stars of a particular song 0-100.
    * <p>
    * @param rating the rating 0-100 to set for rating stars
    * @param trackId the id of the track to update the rating for
    */
   public void controlRating(final long rating, final long trackId) {
      this.fireAction(String.format("%s/ctrl-int/1/setproperty?dacp.userrating=%d&song-spec='dmap.itemid:%d'&session-id=%s", this.getRequestBase(), rating,
               trackId, this.sessionId), false);
   }

   public void controlPlayAlbum(final String albumId, final int tracknum) {

      // http://192.168.254.128:3689/ctrl-int/1/cue?command=clear&session-id=130883770
      // http://192.168.254.128:3689/ctrl-int/1/cue?command=play&query=(('com.apple.itunes.mediakind:1','com.apple.itunes.mediakind:32')+'daap.songartist:Family%20Force%205')&index=0&sort=album&session-id=130883770
      // /ctrl-int/1/cue?command=play&query='daap.songalbumid:16621530181618739404'&index=11&sort=album&session-id=514488449

      // GET
      // /ctrl-int/1/playspec?database-spec='dmap.persistentid:16621530181618731553'&playlist-spec='dmap.persistentid:9378496334192532210'&dacp.shufflestate=1&session-id=514488449
      // (zero based index into playlist)

      ThreadExecutor.runTask(new Runnable() {
         public void run() {
            RequestHelper.attemptRequest(String.format("%s/ctrl-int/1/cue?command=clear&session-id=%s", getRequestBase(), sessionId));
            RequestHelper.attemptRequest(String.format("%s/ctrl-int/1/cue?command=play&query='daap.songalbumid:%s'&index=%d&sort=album&session-id=%s",
                     getRequestBase(), albumId, tracknum, sessionId));

            notifyStatus();
         }
      });

   }

   public void controlQueueAlbum(final String albumId) {
      ThreadExecutor.runTask(new Runnable() {
         public void run() {
            RequestHelper.attemptRequest(String.format("%s/ctrl-int/1/cue?command=add&query='daap.songalbumid:%s'&session-id=%s", getRequestBase(), albumId,
                     sessionId));

            notifyStatus();
         }
      });
   }

   public void controlPlayArtist(String artist, int index) {
      // http://192.168.254.128:3689/ctrl-int/1/cue?command=clear&session-id=130883770
      // /ctrl-int/1/cue?command=play&query=(('com.apple.itunes.mediakind:1','com.apple.itunes.mediakind:32')+'daap.songartist:Family%20Force%205')&index=0&sort=album&session-id=130883770
      // /ctrl-int/1/cue?command=play&query='daap.songartist:%s'&index=0&sort=album&session-id=%s

      final String encodedArtist = Library.escapeUrlString(artist);
      final int encodedIndex = index;

      ThreadExecutor.runTask(new Runnable() {
         public void run() {
            RequestHelper.attemptRequest(String.format("%s/ctrl-int/1/cue?command=clear&session-id=%s", getRequestBase(), sessionId));
            RequestHelper.attemptRequest(String.format("%s/ctrl-int/1/cue?command=play&query='daap.songartist:%s'&index=%d&sort=album&session-id=%s",
                     getRequestBase(), encodedArtist, encodedIndex, sessionId));

            notifyStatus();
         }
      });
   }

   public void controlQueueArtist(String artist) {
      final String encodedArtist = Library.escapeUrlString(artist);

      ThreadExecutor.runTask(new Runnable() {
         public void run() {
            RequestHelper.attemptRequest(String.format("%s/ctrl-int/1/cue?command=add&query='daap.songartist:%s'&session-id=%s", getRequestBase(),
                     encodedArtist, sessionId));
            notifyStatus();
         }
      });
   }

   public void controlQueueTrack(final String trackId) {
      ThreadExecutor.runTask(new Runnable() {
         public void run() {
            RequestHelper.attemptRequest(String.format("%s/ctrl-int/1/cue?command=add&query='dmap.itemid:%s'&session-id=%s", getRequestBase(), trackId,
                     sessionId));
            notifyStatus();
         }
      });
   }

   public void controlPlaySearch(final String search, final int index) {
      // /ctrl-int/1/cue?command=play&query=(('com.apple.itunes.mediakind:1','com.apple.itunes.mediakind:4','com.apple.itunes.mediakind:8')+'dmap.itemname:*F*')&index=4&sort=name&session-id=1550976127
      final String encodedSearch = Library.escapeUrlString(search);

      ThreadExecutor.runTask(new Runnable() {
         public void run() {
            RequestHelper.attemptRequest(String.format("%s/ctrl-int/1/cue?command=clear&session-id=%s", getRequestBase(), sessionId));
            RequestHelper.attemptRequest(String
                     .format("%s/ctrl-int/1/cue?command=play&query=(('com.apple.itunes.mediakind:1','com.apple.itunes.mediakind:4','com.apple.itunes.mediakind:8')+('dmap.itemname:*%s*','daap.songartist:*%s*','daap.songalbum:*%s*'))&type=music&sort=name&index=%d&session-id=%s",
                              getRequestBase(), encodedSearch, encodedSearch, encodedSearch, index, sessionId));
            notifyStatus();
         }
      });
   }

   public void controlPlayPlaylist(final String playlistPersistentId, final String containerItemId) {
      // /ctrl-int/1/playspec?database-spec='dmap.persistentid:0x9031099074C14E05'&container-spec='dmap.persistentid:0xA1E1854E0B9A1B'&container-item-spec='dmap.containeritemid:0x1b47'&session-id=7491138
      final String databasePersistentId = this.databasePersistentId;

      ThreadExecutor.runTask(new Runnable() {
         public void run() {
            RequestHelper.attemptRequest(String
                     .format("%s/ctrl-int/1/playspec?database-spec='dmap.persistentid:0x%s'&container-spec='dmap.persistentid:0x%s'&container-item-spec='dmap.containeritemid:0x%s'&session-id=%s",
                              getRequestBase(), databasePersistentId, playlistPersistentId, containerItemId, sessionId));
            notifyStatus();
         }
      });
   }

   public void controlPlayIndex(final String albumid, final int tracknum) {
      // Attempt to play from current now playing list, otherwise try to play
      // album
      ThreadExecutor.runTask(new Runnable() {
         public void run() {
            try {
               RequestHelper.request(String.format("%s/ctrl-int/1/cue?command=play&index=%d&sort=album&session-id=%s", getRequestBase(), tracknum, sessionId),
                        false);
               // on iTunes this generates a 501 Not Implemented response
            } catch (Exception e) {
               if (albumid != null && albumid.length() > 0) {
                  // Fall back to choosing from the current album if there is
                  // one
                  RequestHelper.attemptRequest(String.format("%s/ctrl-int/1/cue?command=clear&session-id=%s", getRequestBase(), sessionId));
                  RequestHelper.attemptRequest(String.format("%s/ctrl-int/1/cue?command=play&query='daap.songalbumid:%s'&index=%d&sort=album&session-id=%s",
                           getRequestBase(), albumid, tracknum, sessionId));
               }
            }
            notifyStatus();
         }
      });
   }
   
   // Query the media server about the content codes it handles
   // print to stderr as a csv file
   public void listContentCodes() {
	   try {
		   Response contentcodes = RequestHelper.requestParsed(String.format("%s/content-codes?session-id=%s", this
				   .getRequestBase(), this.sessionId), false);

		   for (Response resp : contentcodes.getNested("mccr").findArray("mdcl")) {
			   System.err.println("\"" + resp.getString("mcnm") + "\", \"" +
					   resp.getString("mcna") + "\", \"" +
					   resp.getNumberLong("mcty") + "\"");

		   }
	   } catch (Exception e) {
		   e.printStackTrace();
	   }
   }
}
