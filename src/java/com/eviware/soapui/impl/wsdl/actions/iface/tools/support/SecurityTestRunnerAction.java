/*
 *  soapUI, copyright (C) 2004-2011 eviware.com 
 *
 *  soapUI is free software; you can redistribute it and/or modify it under the 
 *  terms of version 2.1 of the GNU Lesser General Public License as published by 
 *  the Free Software Foundation.
 *
 *  soapUI is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without 
 *  even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. 
 *  See the GNU Lesser General Public License for more details at gnu.org.
 */
package com.eviware.soapui.impl.wsdl.actions.iface.tools.support;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.log4j.Logger;

import com.eviware.soapui.SoapUI;
import com.eviware.soapui.impl.wsdl.WsdlProject;
import com.eviware.soapui.impl.wsdl.actions.iface.tools.soapui.TestRunnerAction;
import com.eviware.soapui.impl.wsdl.support.HelpUrls;
import com.eviware.soapui.model.support.ModelSupport;
import com.eviware.soapui.model.testsuite.TestCase;
import com.eviware.soapui.model.testsuite.TestSuite;
import com.eviware.soapui.security.SecurityTest;
import com.eviware.soapui.support.UISupport;
import com.eviware.soapui.support.types.StringToStringMap;
import com.eviware.x.form.XForm;
import com.eviware.x.form.XFormDialog;
import com.eviware.x.form.XFormDialogBuilder;
import com.eviware.x.form.XFormFactory;
import com.eviware.x.form.XFormField;
import com.eviware.x.form.XFormFieldListener;

public class SecurityTestRunnerAction extends TestRunnerAction
{
	private static final String SH = ".sh";
	private static final String BAT = ".bat";
	private static final String SECURITYTESTRUNNER = "securitytestrunner";
	private static final String SECURITY_TEST_NAME = "SecurityTestName";
	protected static final String TESTRUNNERPATH = "SecurityTestRunner Path";
	
	public static final String SOAPUI_ACTION_ID = "SecurityTestRunnerAction";
	
	private final static Logger log = Logger.getLogger( SecurityTestRunnerAction.class );
	
	protected XFormDialog buildDialog( WsdlProject modelItem )
	{
		if( modelItem == null )
			return null;

		proVersion = isProVersion( modelItem );

		XFormDialogBuilder builder = XFormFactory.createDialogBuilder( "Launch SecurityTestRunner" );
		createTestCaseRunnerTabs( modelItem, builder );

		//TODO: update help URL
		return builder.buildDialog( buildDefaultActions( HelpUrls.TESTRUNNER_HELP_URL, modelItem ),
				"Specify arguments for launching soapUI Security TestRunner", UISupport.TOOL_ICON );
	}

	private void createTestCaseRunnerTabs( WsdlProject modelItem, XFormDialogBuilder builder )
	{
		mainForm = builder.createForm( "Basic" );
		mainForm.addComboBox( TESTSUITE, new String[] {}, "The TestSuite to run" ).addFormFieldListener(
				new XFormFieldListener()
				{

					public void valueChanged( XFormField sourceField, String newValue, String oldValue )
					{
						List<String> testCases = new ArrayList<String>();
						String tc = mainForm.getComponentValue( TESTCASE );

						if( newValue.equals( ALL_VALUE ) )
						{
							for( TestSuite testSuite : testSuites )
							{
								for( TestCase testCase : testSuite.getTestCaseList() )
								{
									if( !testCases.contains( testCase.getName() ) )
										testCases.add( testCase.getName() );
								}
							}
						}
						else
						{
							TestSuite testSuite = getModelItem().getTestSuiteByName( newValue );
							if( testSuite != null )
								testCases.addAll( Arrays.asList( ModelSupport.getNames( testSuite.getTestCaseList() ) ) );
						}

						testCases.add( 0, ALL_VALUE );
						mainForm.setOptions( TESTCASE, testCases.toArray() );

						if( testCases.contains( tc ) )
						{
							mainForm.getFormField( TESTCASE ).setValue( tc );
						}
					}
				} );

		mainForm.addComboBox( TESTCASE, new String[] {}, "The TestCase to run" ).addFormFieldListener( new XFormFieldListener()
		{

			public void valueChanged( XFormField sourceField, String newValue, String oldValue )
			{
				List<String> securityTests = new ArrayList<String>();
				String st = mainForm.getComponentValue( SECURITY_TEST_NAME );

				if( newValue.equals( ALL_VALUE ) )
				{
					for( TestSuite testSuite : testSuites )
					{
						for( TestCase testCase : testSuite.getTestCaseList() )
						{
							for( SecurityTest securityTest : testCase.getSecurityTestList() )
							{
								if( !securityTests.contains( securityTest.getName() ) )
									securityTests.add( securityTest.getName() );
							}
						}
					}
				}
				else
				{
					TestCase testCase = null;
					try
					{
						testCase = getModelItem().getTestSuiteByName( mainForm.getComponentValue( TESTSUITE ) )
								.getTestCaseByName( mainForm.getComponentValue( TESTCASE ) );
					}
					catch( NullPointerException npe )
					{
					}
					if( testCase != null )
						securityTests.addAll( Arrays.asList( ModelSupport.getNames( testCase.getSecurityTestList() ) ) );
				}

				securityTests.add( 0, ALL_VALUE );
				mainForm.setOptions( SECURITY_TEST_NAME, securityTests.toArray() );

				if( securityTests.contains( st ) )
				{
					mainForm.getFormField( SECURITY_TEST_NAME ).setValue( st );
				}
			}
		} );
		mainForm.addComboBox( SECURITY_TEST_NAME, new String[] {}, "The Security Test to run" );
		mainForm.addSeparator();

		mainForm.addCheckBox( ENABLEUI, "Enables UI components in scripts" );
		mainForm.addTextField( TESTRUNNERPATH, "Folder containing SecurityTestRunner.bat to use", XForm.FieldType.FOLDER );
		mainForm.addCheckBox( SAVEPROJECT, "Saves project before running" ).setEnabled( !modelItem.isRemote() );
		mainForm.addCheckBox( ADDSETTINGS, "Adds global settings to command-line" );
		mainForm.addSeparator();
		mainForm.addTextField( PROJECTPASSWORD, "Set project password", XForm.FieldType.PASSWORD );
		mainForm.addTextField( SOAPUISETTINGSPASSWORD, "Set soapui-settings.xml password", XForm.FieldType.PASSWORD );
		mainForm.addCheckBox( IGNOREERRORS, "Do not stop if error occurs, ignore them" );
		mainForm.addCheckBox( SAVEAFTER, "Sets to save the project file after tests have been run" );

		advForm = builder.createForm( "Overrides" );
		advForm.addComboBox( ENDPOINT, new String[] { "" }, "endpoint to forward to" );
		advForm.addTextField( HOSTPORT, "Host:Port to use for requests", XForm.FieldType.TEXT );
		advForm.addSeparator();
		advForm.addTextField( USERNAME, "The username to set for all requests", XForm.FieldType.TEXT );
		advForm.addTextField( PASSWORD, "The password to set for all requests", XForm.FieldType.PASSWORD );
		advForm.addTextField( DOMAIN, "The domain to set for all requests", XForm.FieldType.TEXT );
		advForm.addComboBox( WSSTYPE, new String[] { "", "Text", "Digest" }, "The username to set for all requests" );

		reportForm = builder.createForm( "Reports" );
		reportForm.addCheckBox( PRINTREPORT, "Prints a summary report to the console" );
		reportForm.addCheckBox( EXPORTJUNITRESULTS, "Exports results to a JUnit-Style report" );
		reportForm.addCheckBox( EXPORTALL, "Exports all results (not only errors)" );
		reportForm.addTextField( ROOTFOLDER, "Folder to export to", XForm.FieldType.FOLDER );
		reportForm.addSeparator();
		reportForm.addCheckBox( COVERAGE, "Generate WSDL Coverage report (soapUI Pro only)" ).setEnabled( proVersion );
		reportForm.addCheckBox( OPEN_REPORT, "Opens generated report(s) in browser (soapUI Pro only)" ).setEnabled(
				proVersion );
		reportForm.addTextField( GENERATEREPORTSEACHTESTCASE, "Report to Generate (soapUI Pro only)",
				XForm.FieldType.TEXT ).setEnabled( proVersion );
		reportForm.addTextField( REPORTFORMAT, "Choose report format(s), comma-separated (soapUI Pro only)",
				XForm.FieldType.TEXT ).setEnabled( proVersion );

		propertyForm = builder.createForm( "Properties" );
		propertyForm.addComponent( GLOBALPROPERTIES, createTextArea() );
		propertyForm.addComponent( SYSTEMPROPERTIES, createTextArea() );
		propertyForm.addComponent( PROJECTPROPERTIES, createTextArea() );

		setToolsSettingsAction( null );
		buildArgsForm( builder, false, "TestRunner" );
	}
	
	protected ArgumentBuilder buildArgs( WsdlProject modelItem ) throws IOException
	{
		XFormDialog dialog = getDialog();
		if( dialog == null )
		{
			ArgumentBuilder builder = new ArgumentBuilder( new StringToStringMap() );
			builder.startScript( SECURITYTESTRUNNER, BAT, SH );
			return builder;
		}

		StringToStringMap values = dialog.getValues();

		ArgumentBuilder builder = new ArgumentBuilder( values );

		builder.startScript( "testrunner", ".bat", ".sh" );

		builder.addString( ENDPOINT, "-e", "" );
		builder.addString( HOSTPORT, "-h", "" );

		if( !values.get( TESTSUITE ).equals( ALL_VALUE ) )
			builder.addString( TESTSUITE, "-s", "" );

		if( !values.get( TESTCASE ).equals( ALL_VALUE ) )
			builder.addString( TESTCASE, "-c", "" );
		
		if( !values.get( SECURITY_TEST_NAME ).equals( ALL_VALUE ) )
			builder.addString( SECURITY_TEST_NAME, "-n", "" );

		builder.addString( USERNAME, "-u", "" );
		builder.addStringShadow( PASSWORD, "-p", "" );
		builder.addString( DOMAIN, "-d", "" );
		builder.addString( WSSTYPE, "-w", "" );

		builder.addBoolean( PRINTREPORT, "-r" );
		builder.addBoolean( EXPORTALL, "-a" );
		builder.addBoolean( EXPORTJUNITRESULTS, "-j" );
		builder.addString( ROOTFOLDER, "-f", "" );

		if( proVersion )
		{
			builder.addBoolean( OPEN_REPORT, "-o" );
			builder.addBoolean( COVERAGE, "-g" );
			builder.addString( GENERATEREPORTSEACHTESTCASE, "-R", "" );
			builder.addString( REPORTFORMAT, "-F", "" );
		}

		builder.addStringShadow( PROJECTPASSWORD, "-x", "" );
		builder.addStringShadow( SOAPUISETTINGSPASSWORD, "-v", "" );
		builder.addBoolean( IGNOREERRORS, "-I" );
		builder.addBoolean( SAVEAFTER, "-S" );

		addPropertyArguments( builder );

		if( dialog.getBooleanValue( ADDSETTINGS ) )
		{
			try
			{
				builder.addBoolean( ADDSETTINGS, "-t" + SoapUI.saveSettings() );
			}
			catch( Exception e )
			{
				SoapUI.logError( e );
			}
		}

		builder.addBoolean( ENABLEUI, "-i" );
		builder.addArgs( new String[] { modelItem.getPath() } );

		addToolArgs( values, builder );

		return builder;
	}
	
}