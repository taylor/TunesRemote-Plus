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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import org.tunesremote.daap.Library;
import org.tunesremote.daap.Response;
import org.tunesremote.daap.Session;

import android.app.ListActivity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.TextView;

public class NowPlayingActivity extends ListActivity {

   public final static String TAG = TracksActivity.class.toString();
   protected BackendService backend;
   protected Session session;
   protected Library library;
   protected NowPlayingAdapter adapter;
   protected String albumid;
   protected boolean iTunes = false;

   public ServiceConnection connection = new ServiceConnection() {
      public void onServiceConnected(ComponentName className, IBinder service) {
         try {
            backend = ((BackendService.BackendBinder) service).getService();
            session = backend.getSession();

            if (session == null)
               return;

            adapter.results.clear();

            // begin search now that we have a backend
            library = new Library(session);
            iTunes = library.readNowPlaying(albumid, adapter);
         } catch (Exception e) {
            Log.e(TAG, "onServiceConnected:" + e.getMessage());
         }
      }

      public void onServiceDisconnected(ComponentName className) {
         backend = null;
         session = null;

      }
   };

   public Handler resultsUpdated = new Handler() {
      @Override
      public void handleMessage(Message msg) {
         adapter.notifyDataSetChanged();
      }
   };

   @Override
   public void onStart() {
      super.onStart();
      this.bindService(new Intent(this, BackendService.class), connection, Context.BIND_AUTO_CREATE);

   }

   @Override
   public void onStop() {
      super.onStop();
      this.unbindService(connection);

   }

   @Override
   public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      setContentView(R.layout.act_nowplaying);

      this.albumid = this.getIntent().getStringExtra(Intent.EXTRA_TITLE);

      this.getListView().setOnItemClickListener(new OnItemClickListener() {
         public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            if (iTunes) {
               session.controlPlayAlbum(albumid, position);
            } else {
               session.controlPlayIndex(albumid, position);
            }
            setResult(RESULT_OK, new Intent());
            finish();
         }
      });

      this.adapter = new NowPlayingAdapter(this);
      this.setListAdapter(adapter);
   }

   protected class NowPlayingAdapter extends BaseAdapter implements TagListener {
      protected Context context;
      protected LayoutInflater inflater;
      protected SimpleDateFormat format = new SimpleDateFormat("m:ss");
      protected Date date = new Date(0);

      protected List<Response> results = new LinkedList<Response>();

      public NowPlayingAdapter(Context context) {
         this.context = context;
         this.inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

      }

      public int getCount() {
         return results.size();
      }

      public Object getItem(int position) {
         return results.get(position);
      }

      public long getItemId(int position) {
         return position;
      }

      public View getView(int position, View convertView, ViewGroup parent) {
         if (convertView == null)
            convertView = inflater.inflate(R.layout.item_nowplaying_track, parent, false);

         try {

            // otherwise show normal search result
            Response resp = (Response) this.getItem(position);

            final String title = resp.getString("minm");
            final String artist = resp.getString("asar");
            date.setTime(resp.getNumberLong("astm"));
            final String length = format.format(date);
            final long trackId = resp.getNumberLong("miid");
            final long currentTrackId = ControlActivity.status.getTrackId();

            TextView txtTitle = ((TextView) convertView.findViewById(android.R.id.text1));
            txtTitle.setText(title);

            TextView txtLength = ((TextView) convertView.findViewById(android.R.id.text2));
            txtLength.setText(length);

            TextView txtArtist = ((TextView) convertView.findViewById(R.id.artist));
            txtArtist.setText(artist);

            // highlight the current track playing
            if (currentTrackId == trackId) {
               Log.i(TAG, "Track Ids match! = " + trackId);
               txtTitle.setTextColor(Color.CYAN);
               txtLength.setTextColor(Color.CYAN);
               txtArtist.setTextColor(Color.CYAN);
            } else {
               txtTitle.setTextColor(Color.WHITE);
               txtLength.setTextColor(Color.WHITE);
               txtArtist.setTextColor(Color.WHITE);
            }
         } catch (Exception e) {
            Log.d(TAG, String.format("onCreate Error: %s", e.getMessage()));
         }

         /*
          * mlit --+ mikd 1 02 == 2 asal 12 Dance or Die asar 14 Family Force 5 astm 4 0003d5d6 == 251350 astn 2 0001
          * miid 4 0000005b == 91 minm 12 dance or die
          */

         return convertView;
      }

      public void foundTag(String tag, Response resp) {
         // add a found search result to our list
         if (resp.containsKey("minm"))
            results.add(resp);
         this.searchDone();
      }

      public void searchDone() {
         resultsUpdated.removeMessages(-1);
         resultsUpdated.sendEmptyMessage(-1);

      }

   }
}
