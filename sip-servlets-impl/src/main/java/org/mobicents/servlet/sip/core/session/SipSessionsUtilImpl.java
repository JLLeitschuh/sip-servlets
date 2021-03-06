/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2014, Telestax Inc and individual contributors
 * by the @authors tag.
 *
 * This program is free software: you can redistribute it and/or modify
 * under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */

package org.mobicents.servlet.sip.core.session;

import gov.nist.javax.sip.header.extensions.JoinHeader;
import gov.nist.javax.sip.header.extensions.ReplacesHeader;

import java.io.Serializable;
import java.text.ParseException;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipSession;

import org.apache.log4j.Logger;
import org.mobicents.javax.servlet.sip.SipApplicationSessionAsynchronousWork;
import org.mobicents.javax.servlet.sip.SipSessionAsynchronousWork;
import org.mobicents.servlet.sip.core.SipContext;

/**
 * @author Jean Deruelle
 *
 */
public class SipSessionsUtilImpl implements MobicentsSipSessionsUtil, Serializable {
	private static final long serialVersionUID = 1L;
	private static final Logger logger = Logger.getLogger(SipSessionsUtilImpl.class);
	
	private transient SipContext sipContext;
	
	private transient ConcurrentHashMap<MobicentsSipSessionKey, MobicentsSipSession> joinSession;
	private transient ConcurrentHashMap<MobicentsSipSessionKey, MobicentsSipSession> replacesSession;
	
	private ConcurrentHashMap<MobicentsSipApplicationSessionKey, MobicentsSipApplicationSessionKey> joinApplicationSession;
	private ConcurrentHashMap<MobicentsSipApplicationSessionKey, MobicentsSipApplicationSessionKey> replacesApplicationSession;

	public SipSessionsUtilImpl(SipContext sipContext) {
		this.sipContext = sipContext;
		joinSession = new ConcurrentHashMap<MobicentsSipSessionKey, MobicentsSipSession>();
		replacesSession = new ConcurrentHashMap<MobicentsSipSessionKey, MobicentsSipSession>();
		joinApplicationSession = new ConcurrentHashMap<MobicentsSipApplicationSessionKey, MobicentsSipApplicationSessionKey>();
		replacesApplicationSession = new ConcurrentHashMap<MobicentsSipApplicationSessionKey, MobicentsSipApplicationSessionKey>();
	}
	
	/**
	 * {@inheritDoc}
	 */
	public SipApplicationSession getApplicationSessionById(String applicationSessionId) {
		return getApplicationSessionById(applicationSessionId, true);
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.mobicents.javax.servlet.sip.SipSessionsUtilExt#getApplicationSessionById(java.lang.String, boolean)
	 */
	public SipApplicationSession getApplicationSessionById(String applicationSessionId, boolean isContainerManaged) {
		if(logger.isDebugEnabled()) {
			logger.debug("getApplicationSessionById - applicationSessionId=" + applicationSessionId);
		}
		if(applicationSessionId == null) {
			throw new NullPointerException("the given id is null !");
		}
		SipApplicationSessionKey applicationSessionKey;
		try {
			applicationSessionKey = SessionManagerUtil.parseSipApplicationSessionKey(applicationSessionId);
		} catch (ParseException e) {
			logger.error("the given application session id : " + applicationSessionId + 
					" couldn't be parsed correctly ",e);
			return null;
		}
		if(applicationSessionKey.getApplicationName().equals(sipContext.getApplicationName())) {
			MobicentsSipApplicationSession sipApplicationSession = sipContext.getSipManager().getSipApplicationSession(applicationSessionKey, false);
			if(sipApplicationSession == null) {
				return null;
			} else {
				// make sure to acquire this app session and add it to the set of app sessions we monitor in the context of the application
				// to release them all when we exit application code
				sipContext.enterSipApp(sipApplicationSession, null, true, isContainerManaged);
				return sipApplicationSession.getFacade();
			}
		} else {
			logger.warn("the given application session id : " + applicationSessionId + 
					" tried to be retrieved from incorret application " + sipContext.getApplicationName());
			return null;
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public SipApplicationSession getApplicationSessionByKey(String applicationSessionKey,
			boolean create) {
		return getApplicationSessionByKey(applicationSessionKey, create, true);
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.mobicents.javax.servlet.sip.SipSessionsUtilExt#getApplicationSessionByKey(java.lang.String, boolean, boolean)
	 */
	public SipApplicationSession getApplicationSessionByKey(String applicationSessionKey,
			boolean create, boolean isContainerManaged) {
		if(logger.isDebugEnabled()) {
			logger.debug("getApplicationSessionByKey - applicationSessionKey=" + applicationSessionKey);
		}
		
		if(applicationSessionKey == null) {
			throw new NullPointerException("the given key is null !");
		}
		SipApplicationSessionKey sipApplicationSessionKey = new SipApplicationSessionKey(null, sipContext.getApplicationName(), applicationSessionKey);
		
		MobicentsSipApplicationSession sipApplicationSession = sipContext.getSipManager().getSipApplicationSession(sipApplicationSessionKey, create);
		if(sipApplicationSession == null) {
			return null;
		} else {
			// make sure to acquire this app session and add it to the set of app sessions we monitor in the context of the application
			// to release them all when we exit application code
			sipContext.enterSipApp(sipApplicationSession, null, true, isContainerManaged);
			return sipApplicationSession.getFacade();
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public SipSession getCorrespondingSipSession(SipSession sipSession, String headerName) {
		MobicentsSipSession correspondingSipSession = null;
		if(headerName.equalsIgnoreCase(JoinHeader.NAME)) {
			correspondingSipSession = joinSession.get(((MobicentsSipSession) sipSession).getKey());
		} else if (headerName.equalsIgnoreCase(ReplacesHeader.NAME)) {
			correspondingSipSession = replacesSession.get(((MobicentsSipSession) sipSession).getKey());
		} else {
			throw new IllegalArgumentException("headerName argument should either be one of Join or Replaces");
		}
		return correspondingSipSession;
	}
	
	/**
	 * Add a mapping between a new session and a corresponding sipSession related to a headerName. See Also getCorrespondingSipSession method.
	 * @param newSession the new session
	 * @param correspondingSipSession the corresponding sip session to add
	 * @param headerName the header name
	 */
	public void addCorrespondingSipSession(MobicentsSipSession newSession, MobicentsSipSession correspondingSipSession, String headerName) {
		if(JoinHeader.NAME.equalsIgnoreCase(headerName)) {
			joinSession.putIfAbsent(newSession.getKey(), correspondingSipSession);
		} else if (ReplacesHeader.NAME.equalsIgnoreCase(headerName)) {
			replacesSession.putIfAbsent(newSession.getKey(), correspondingSipSession);
		} else {
			throw new IllegalArgumentException("headerName argument should either be one of Join or Replaces, was : " + headerName);
		}
	}
	
	/**
	 * Add a mapping between a corresponding sipSession related to a headerName. See Also getCorrespondingSipSession method.
	 * @param correspondingSipSession the corresponding sip session to add
	 * @param headerName the header name
	 */
	public void removeCorrespondingSipSession(MobicentsSipSessionKey sipSession) {
		joinSession.remove(sipSession);
		replacesSession.remove(sipSession);
	}
	
	/**
	 * {@inheritDoc}
	 */
	public MobicentsSipApplicationSessionKey getCorrespondingSipApplicationSession(MobicentsSipApplicationSessionKey sipApplicationSessionKey, String headerName) {
		MobicentsSipApplicationSessionKey correspondingSipApplicationSession = null;
		if(headerName.equalsIgnoreCase(JoinHeader.NAME)) {
			correspondingSipApplicationSession = joinApplicationSession.get(sipApplicationSessionKey);
		} else if (headerName.equalsIgnoreCase(ReplacesHeader.NAME)) {
			correspondingSipApplicationSession = replacesApplicationSession.get(sipApplicationSessionKey);
		} else {
			throw new IllegalArgumentException("headerName argument should either be one of Join or Replaces");
		}
		return correspondingSipApplicationSession;
	}
	
	/**
	 * Add a mapping between a new session and a corresponding sipSession related to a headerName. See Also getCorrespondingSipSession method.
	 * @param newSession the new session
	 * @param correspondingSipSession the corresponding sip session to add
	 * @param headerName the header name
	 */
	public void addCorrespondingSipApplicationSession(MobicentsSipApplicationSessionKey newApplicationSession, MobicentsSipApplicationSessionKey correspondingSipApplicationSession, String headerName) {
		if(headerName.equalsIgnoreCase(JoinHeader.NAME)) {
			joinApplicationSession.putIfAbsent(newApplicationSession, correspondingSipApplicationSession);
		} else if (headerName.equalsIgnoreCase(ReplacesHeader.NAME)) {
			replacesApplicationSession.putIfAbsent(newApplicationSession, correspondingSipApplicationSession);
		} else {
			throw new IllegalArgumentException("headerName argument should either be one of Join or Replaces");
		}
	}
	
	/**
	 * Add a mapping between a corresponding sipSession related to a headerName. See Also getCorrespondingSipSession method.
	 * @param correspondingSipSession the corresponding sip session to add
	 * @param headerName the header name
	 */
	public void removeCorrespondingSipApplicationSession(MobicentsSipApplicationSessionKey sipApplicationSession) {
		joinApplicationSession.remove(sipApplicationSession);
		replacesApplicationSession.remove(sipApplicationSession);
		Iterator<MobicentsSipApplicationSessionKey> it = joinApplicationSession.values().iterator();
		boolean found = false;
		while (it.hasNext() && !found) {
			MobicentsSipApplicationSessionKey sipApplicationSessionKey = it.next();
			if(sipApplicationSessionKey.equals(sipApplicationSession)) {
				joinApplicationSession.remove(sipApplicationSessionKey);
				found = true;
			}
		}
		it = replacesApplicationSession.values().iterator();
		found = false;
		while (it.hasNext() && !found) {
			MobicentsSipApplicationSessionKey sipApplicationSessionKey = it.next();
			if(sipApplicationSessionKey.equals(sipApplicationSession)) {
				replacesApplicationSession.remove(sipApplicationSessionKey);
				found = true;
			}
		}
	}

	@Override
	public void scheduleAsynchronousWork(String sipSessionId,
			SipSessionAsynchronousWork work) {
		SipSessionKey sipSessionKey;
		try {
			sipSessionKey = SessionManagerUtil.parseSipSessionKey(sipSessionId);
		} catch (ParseException e) {
			throw new IllegalArgumentException("the given application session id : " + sipSessionId + 
					" couldn't be parsed correctly ",e);
		}
		sipContext.getSipApplicationDispatcher().getAsynchronousExecutor().execute(new SipSessionAsyncTask(sipSessionKey, work, sipContext.getSipApplicationDispatcher().getSipFactory()));
	}

	@Override
	public void scheduleAsynchronousWork(String sipApplicationSessionId,
			SipApplicationSessionAsynchronousWork work) {
		SipApplicationSessionKey applicationSessionKey;
		try {
			applicationSessionKey = SessionManagerUtil.parseSipApplicationSessionKey(sipApplicationSessionId );
		} catch (ParseException e) {
			throw new IllegalArgumentException("the given application session id : " + sipApplicationSessionId + 
					" couldn't be parsed correctly ",e);
		}
		sipContext.getSipApplicationDispatcher().getAsynchronousExecutor().execute(new SipApplicationSessionAsyncTask(applicationSessionKey, work, sipContext.getSipApplicationDispatcher().getSipFactory()));
	}
}
