package com.irccloud.android;

import android.annotation.SuppressLint;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.TreeMap;

@SuppressLint("UseSparseArrays")
public class EventsDataSource {

	public class comparator implements Comparator<IRCCloudJSONObject> {
		public int compare(IRCCloudJSONObject e1, IRCCloudJSONObject e2) {
			long l1 = e1.eid(), l2 = e2.eid();
			if(l1 == l2)
				return 0;
			else if(l1 > l2)
				return 1;
			else return -1;
		}
	}
	
	private HashMap<Integer,TreeMap<Long, IRCCloudJSONObject>> events;
	
	private static EventsDataSource instance = null;
	
	public static EventsDataSource getInstance() {
		if(instance == null)
			instance = new EventsDataSource();
		return instance;
	}

	public EventsDataSource() {
		events = new HashMap<Integer,TreeMap<Long, IRCCloudJSONObject>>();
	}

	public void clear() {
		synchronized(events) {
			events.clear();
		}
	}
	
	public void addEvent(IRCCloudJSONObject event) {
		synchronized(events) {
			if(!events.containsKey(event.bid()))
				events.put(event.bid(), new TreeMap<Long,IRCCloudJSONObject>());
			events.get(event.bid()).put(event.eid(), event);
		}
	}

	public IRCCloudJSONObject getEvent(long eid, int bid) {
		synchronized(events) {
			if(events.containsKey(bid))
				return events.get(bid).get(eid);
		}
		return null;
	}
	
	public void deleteEvent(long eid, int bid) {
		synchronized(events) {
			if(events.containsKey(bid) && events.get(bid).containsKey(eid))
				events.get(bid).remove(eid);
		}
	}

	public void deleteEventsForBuffer(int bid) {
		synchronized(events) {
			if(events.containsKey(bid))
				events.remove(bid);
		}
	}

	public ArrayList<IRCCloudJSONObject> getEventsForBuffer(int bid) {
		ArrayList<IRCCloudJSONObject> list = new ArrayList<IRCCloudJSONObject>();
		synchronized(events) {
			if(events.containsKey(bid)) {
				Iterator<IRCCloudJSONObject> i = events.get(bid).values().iterator();
				while(i.hasNext()) {
					list.add(i.next());
				}
				//Collections.sort(list, new comparator());
			}
		}
		return list;
	}

	public int getUnreadCountForBuffer(int bid, long last_seen_eid) {
		int count = 0;
		synchronized(events) {
			if(events.containsKey(bid)) {
				Iterator<IRCCloudJSONObject> i = events.get(bid).values().iterator();
				while(i.hasNext()) {
					IRCCloudJSONObject e = i.next();
					String type = e.type();
					if(e.eid() > last_seen_eid && (type.equals("buffer_msg") || type.equals("buffer_me_msg") || type.equals("notice")))
						count++;
				}
			}
		}
		return count;
	}

	public synchronized int getHighlightCountForBuffer(int bid, long last_seen_eid) {
		int count = 0;
		synchronized(events) {
			if(events.containsKey(bid)) {
				Iterator<IRCCloudJSONObject> i = events.get(bid).values().iterator();
				while(i.hasNext()) {
					IRCCloudJSONObject e = i.next();
					if(e.eid() > last_seen_eid && e.highlight())
						count++;
				}
			}
		}
		return count;
	}
}
