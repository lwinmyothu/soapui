package com.eviware.soapui.impl.rest;/*
 * soapUI, copyright (C) 2004-2013 smartbear.com
 *
 * soapUI is free software; you can redistribute it and/or modify it under the
 * terms of version 2.1 of the GNU Lesser General Public License as published by
 * the Free Software Foundation.
 *
 * soapUI is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details at gnu.org.
 *
 */

import com.eviware.soapui.impl.URIParser;

/**
 * Class Description.
 * Author: Shadid Chowdhury
 */
public interface RestURIParser extends URIParser
{

	/**
	 * @return
	 */
	public String getEndpoint();

	/**
	 * @return
	 */
	public String getResourceName();

	//TODO: Consider adding "getResourcePath" instead of "getPath", more descriptive name.

	/**
	 * @return
	 */
	public String getParams();
}