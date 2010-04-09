/*
 * Copyright (C) 2010 Geometer Plus <contact@geometerplus.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301, USA.
 */

package org.geometerplus.zlibrary.core.network;

import java.io.InputStream;
import java.io.IOException;


public abstract class ZLNetworkRequest {

	public static final int AUTHENTICATION_NO_AUTH = 0;
	public static final int AUTHENTICATION_BASIC = 1;


	public final String URL;
	public final String SSLCertificate;

	public int AuthenticationMethod = AUTHENTICATION_NO_AUTH;
	public String UserName;
	public String Password;

	public boolean FollowRedirects = true;

	private String myErrorMessage;


	protected ZLNetworkRequest(String url, String sslCertificate) {
		URL = url;
		SSLCertificate = sslCertificate;
	}

	public String getErrorMessage() {
		return myErrorMessage;
	}

	protected void setErrorMessage(String errorMessage) {
		myErrorMessage = errorMessage;
	}

	public abstract boolean doBefore();
	public abstract boolean handleStream(InputStream inputStream) throws IOException;

	// When `success == true` return false MUST make request fail; when `success == false` return value MUST be ignored.
	public abstract boolean doAfter(boolean success);
}