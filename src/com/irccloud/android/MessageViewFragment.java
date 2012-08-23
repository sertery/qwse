package com.irccloud.android;

import java.util.Iterator;
import java.util.TreeMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.webkit.WebView;
import android.webkit.WebChromeClient;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockFragment;

@SuppressLint("SetJavaScriptEnabled")
public class MessageViewFragment extends SherlockFragment {
	private NetworkConnection conn;
	private WebView webView;
	private TextView topicView;
	private TextView statusView;
	private int cid;
	private long bid;
	private long last_seen_eid;
	private long min_eid;
	private long earliest_eid;
	private String name;
	
	public class JavaScriptInterface {
		public TreeMap<Long,IRCCloudJSONObject> incomingBacklog;
		
		public void requestBacklog() {
			BaseActivity a = (BaseActivity) getActivity();
			a.setSupportProgressBarIndeterminate(true);
			conn.request_backlog(cid, bid, earliest_eid);
		}

		public void log(String msg) {
			Log.i("IRCCloud", msg);
		}
		
	    public void showToast(String toast) {
	        Toast.makeText(getActivity(), toast, Toast.LENGTH_SHORT).show();
	    }
	    
	    public String getIncomingBacklog() {
	    	JSONArray array = new JSONArray();
	    	if(incomingBacklog != null) {
	    		Iterator<IRCCloudJSONObject> i = incomingBacklog.values().iterator();
	    		while(i.hasNext()) {
	    			array.put(i.next().getObject());
	    		}
	    	}
	    	incomingBacklog = null;
	    	return array.toString();
	    }
	}
	
	private JavaScriptInterface jsInterface = new JavaScriptInterface();
	
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    	final View v = inflater.inflate(R.layout.messageview, container, false);
    	v.getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
    		@Override
    		public void onGlobalLayout() {
    			mHandler.postDelayed(new Runnable() {
					@Override
					public void run() {
		    			webView.loadUrl("javascript:window.scrollTo(0, document.body.scrollHeight)");
		    			webView.invalidate();
					}
    			}, 100);
    		}
   		}); 
    	webView = (WebView)v.findViewById(R.id.messageview);
    	webView.getSettings().setJavaScriptEnabled(true);
    	webView.addJavascriptInterface(jsInterface, "Android");
    	webView.setWebChromeClient(new WebChromeClient() {
    		  public void onConsoleMessage(String message, int lineNumber, String sourceID) {
    		    Log.d("IRCCloud", message + " -- From line "
    		                         + lineNumber + " of "
    		                         + sourceID);
    		  }
    		});
    	webView.loadUrl("file:///android_asset/messageview.html");
    	webView.pageDown(true);
    	topicView = (TextView)v.findViewById(R.id.topicView);
    	statusView = (TextView)v.findViewById(R.id.statusView);
    	return v;
    }
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(savedInstanceState != null && savedInstanceState.containsKey("cid")) {
        	cid = savedInstanceState.getInt("cid");
        	bid = savedInstanceState.getLong("bid");
        	name = savedInstanceState.getString("name");
        	last_seen_eid = savedInstanceState.getLong("last_seen_eid");
        	min_eid = savedInstanceState.getLong("min_eid");
        }
    }
    
    @Override
    public void onSaveInstanceState(Bundle state) {
    	super.onSaveInstanceState(state);
    	state.putInt("cid", cid);
    	state.putLong("bid", bid);
    	state.putLong("last_seen_eid", last_seen_eid);
    	state.putLong("min_eid", min_eid);
    	state.putString("name", name);
    }

    
    public void onAttach(Activity activity) {
    	super.onAttach(activity);
    	if(activity.getIntent() != null && activity.getIntent().hasExtra("cid")) {
	    	cid = activity.getIntent().getIntExtra("cid", 0);
	    	bid = activity.getIntent().getLongExtra("bid", 0);
	    	last_seen_eid = activity.getIntent().getLongExtra("last_seen_eid", 0);
	    	min_eid = activity.getIntent().getLongExtra("min_eid", 0);
	    	name = activity.getIntent().getStringExtra("name");
    	}
    }

    private void insertEvent(IRCCloudJSONObject event) {
    	if(event.eid() == min_eid)
	    	webView.loadUrl("javascript:hideBacklogBtn()");
    	if(event.eid() < earliest_eid)
    		earliest_eid = event.eid();
    	webView.loadUrl("javascript:appendEvent(("+event.toString()+"))");
    }
    
    public void onResume() {
    	super.onResume();
    	conn = NetworkConnection.getInstance();
    	conn.addHandler(mHandler);
    	if(bid != -1)
    		new RefreshTask().execute((Void)null);
    }
    
    private class HeartbeatTask extends AsyncTask<IRCCloudJSONObject, Void, Void> {

		@Override
		protected Void doInBackground(IRCCloudJSONObject... params) {
			IRCCloudJSONObject e = params[0];
			
	    	if(e.eid() > last_seen_eid) {
	    		getActivity().getIntent().putExtra("last_seen_eid", e.eid());
	    		NetworkConnection.getInstance().heartbeat(bid, e.cid(), e.bid(), e.eid());
	    		last_seen_eid = e.eid();
	    	}
			return null;
		}
    }
    
	private class RefreshTask extends AsyncTask<Void, Void, Void> {
		TreeMap<Long,IRCCloudJSONObject> events;
		ChannelsDataSource.Channel channel;
		ServersDataSource.Server server;
		
		@Override
		protected Void doInBackground(Void... params) {
			channel = ChannelsDataSource.getInstance().getChannelForBuffer(bid);
			server = ServersDataSource.getInstance().getServer(cid);
			long time = System.currentTimeMillis();
			events = EventsDataSource.getInstance().getEventsForBuffer((int)bid);
			Log.i("IRCCloud", "Loaded data in " + (System.currentTimeMillis() - time) + "ms");
			return null;
		}

		@Override
		protected void onPostExecute(Void result) {
			if(events.size() == 0 && min_eid > 0) {
				conn.request_backlog(cid, bid, 0);
			} else if(events.size() > 0){
    			earliest_eid = events.firstKey();
    			if(events.firstKey() > min_eid)
    		    	webView.loadUrl("javascript:showBacklogBtn()");
    			jsInterface.incomingBacklog = events;
		    	webView.loadUrl("javascript:appendBacklog()");
		    	if(events.size() > 0)
		    		new HeartbeatTask().execute(events.get(events.lastKey()));
			}
	    	if(channel != null && channel.topic_text != null && channel.topic_text.length() > 0) {
	    		topicView.setVisibility(View.VISIBLE);
	    		topicView.setText(channel.topic_text);
	    	} else {
	    		topicView.setVisibility(View.GONE);
	    		topicView.setText("");
	    	}
	    	try {
				update_status(server.status, new JSONObject(server.fail_info));
			} catch (JSONException e) {
				e.printStackTrace();
			}
		}
	}

	private class StatusRefreshRunnable implements Runnable {
		String status;
		JSONObject fail_info;
		
		public StatusRefreshRunnable(String status, JSONObject fail_info) {
			this.status = status;
			this.fail_info = fail_info;
		}

		@Override
		public void run() {
			update_status(status, fail_info);
		}
	}
	
	StatusRefreshRunnable statusRefreshRunnable = null;
	
	private void update_status(String status, JSONObject fail_info) {
		if(statusRefreshRunnable != null) {
			mHandler.removeCallbacks(statusRefreshRunnable);
			statusRefreshRunnable = null;
		}
		
    	if(status.equals("connected_ready")) {
    		statusView.setVisibility(View.GONE);
    		statusView.setText("");
    	} else if(status.equals("quitting")) {
    		statusView.setVisibility(View.VISIBLE);
    		statusView.setText("Disconnecting");
    		statusView.setTextColor(getResources().getColor(R.color.dark_blue));
    		statusView.setBackgroundResource(R.drawable.background_blue);
    	} else if(status.equals("disconnected")) {
    		statusView.setVisibility(View.VISIBLE);
    		statusView.setText("Disconnected");
    		statusView.setTextColor(getResources().getColor(R.color.dark_blue));
    		statusView.setBackgroundResource(R.drawable.background_blue);
    	} else if(status.equals("queued")) {
    		statusView.setVisibility(View.VISIBLE);
    		statusView.setText("Connection queued");
    		statusView.setTextColor(getResources().getColor(R.color.dark_blue));
    		statusView.setBackgroundResource(R.drawable.background_blue);
    	} else if(status.equals("connecting")) {
    		statusView.setVisibility(View.VISIBLE);
    		statusView.setText("Connecting");
    		statusView.setTextColor(getResources().getColor(R.color.dark_blue));
    		statusView.setBackgroundResource(R.drawable.background_blue);
    	} else if(status.equals("connected")) {
    		statusView.setVisibility(View.VISIBLE);
    		statusView.setText("Connected");
    		statusView.setTextColor(getResources().getColor(R.color.dark_blue));
    		statusView.setBackgroundResource(R.drawable.background_blue);
    	} else if(status.equals("connected_joining")) {
    		statusView.setVisibility(View.VISIBLE);
    		statusView.setText("Connected: Joining Channels");
    		statusView.setTextColor(getResources().getColor(R.color.dark_blue));
    		statusView.setBackgroundResource(R.drawable.background_blue);
    	} else if(status.equals("pool_unavailable")) {
    		statusView.setVisibility(View.VISIBLE);
    		statusView.setText("Pool unavailable");
    		statusView.setTextColor(getResources().getColor(R.color.dark_blue));
    		statusView.setBackgroundResource(R.drawable.background_blue);
    	} else if(status.equals("waiting_to_retry")) {
    		try {
	    		statusView.setVisibility(View.VISIBLE);
	    		long seconds = (fail_info.getLong("timestamp") + fail_info.getInt("retry_timeout")) - System.currentTimeMillis()/1000;
	    		statusView.setText("Disconnected: " + fail_info.getString("reason") + ". Reconnecting in " + seconds + " seconds.");
	    		statusView.setTextColor(getResources().getColor(R.color.status_fail_text));
	    		statusView.setBackgroundResource(R.drawable.status_fail_bg);
	    		statusRefreshRunnable = new StatusRefreshRunnable(status, fail_info);
	    		mHandler.postDelayed(statusRefreshRunnable, 500);
    		} catch (JSONException e) {
    			e.printStackTrace();
    		}
    	} else if(status.equals("ip_retry")) {
    		statusView.setVisibility(View.VISIBLE);
    		statusView.setText("Trying another IP address");
    		statusView.setTextColor(getResources().getColor(R.color.dark_blue));
    		statusView.setBackgroundResource(R.drawable.background_blue);
    	}
	}
	
    public void onPause() {
    	super.onPause();
		if(statusRefreshRunnable != null) {
			mHandler.removeCallbacks(statusRefreshRunnable);
			statusRefreshRunnable = null;
		}
    	if(conn != null)
    		conn.removeHandler(mHandler);
   	}
    
	private final Handler mHandler = new Handler() {
		IRCCloudJSONObject e;
		
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case NetworkConnection.EVENT_STATUSCHANGED:
				try {
					IRCCloudJSONObject object = (IRCCloudJSONObject)msg.obj;
					if(object.getInt("cid") == cid) {
						update_status(object.getString("new_status"), object.getJSONObject("fail_info"));
					}
				} catch (JSONException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				break;
			case NetworkConnection.EVENT_MAKEBUFFER:
				BuffersDataSource.Buffer buffer = (BuffersDataSource.Buffer)msg.obj;
				if(bid == -1 && buffer.cid == cid && buffer.name.equalsIgnoreCase(name)) {
					bid = buffer.bid;
				}
				break;
			case NetworkConnection.EVENT_DELETEBUFFER:
				if((Integer)msg.obj == bid) {
	                Intent parentActivityIntent = new Intent(getActivity(), MainActivity.class);
	                parentActivityIntent.addFlags(
	                        Intent.FLAG_ACTIVITY_CLEAR_TOP |
	                        Intent.FLAG_ACTIVITY_NEW_TASK);
	                getActivity().startActivity(parentActivityIntent);
	                getActivity().finish();
				}
				break;
			case NetworkConnection.EVENT_BACKLOG_END:
				new RefreshTask().execute((Void)null);
				break;
			case NetworkConnection.EVENT_CHANNELTOPIC:
		    	try {
					e = (IRCCloudJSONObject)msg.obj;
					if(e.bid() == bid) {
			    		topicView.setVisibility(View.VISIBLE);
						topicView.setText(e.getString("topic"));
					}
				} catch (JSONException e1) {
					e1.printStackTrace();
				}
			case NetworkConnection.EVENT_JOIN:
			case NetworkConnection.EVENT_PART:
			case NetworkConnection.EVENT_NICKCHANGE:
			case NetworkConnection.EVENT_QUIT:
			case NetworkConnection.EVENT_BUFFERMSG:
			case NetworkConnection.EVENT_USERCHANNELMODE:
				e = (IRCCloudJSONObject)msg.obj;
				if(e.bid() == bid) {
					insertEvent(e);
					new HeartbeatTask().execute(e);
				}
				break;
			default:
				break;
			}
		}
	};
}
