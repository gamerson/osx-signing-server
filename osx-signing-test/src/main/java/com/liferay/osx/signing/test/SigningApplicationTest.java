/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.osx.signing.test;

import aQute.lib.io.IO;

import java.io.File;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.stream.Stream;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.util.tracker.ServiceTracker;

/**
 * @author Gregory Amerson
 */
public class SigningApplicationTest {

	@After
	public void after() {
		_clientBuilderTracker.close();
	}

	@Before
	public void before() {
		_clientBuilderTracker = new ServiceTracker<>(
			_bundleContext, ClientBuilder.class, null);

		_clientBuilderTracker.open();
	}

	@Test
	public void testCodesignBadIdentity() throws Exception {
		String tmpAppDir = _extractTestApp(
			"unsigned/LiferayWorkspace-1.5.0-osx-installer.app");

		MultivaluedHashMap<String, String> form = new MultivaluedHashMap<>();

		form.add("identity", "foo");
		form.add("path", tmpAppDir);

		Client client = _createClient();

		WebTarget target = client.target("http://localhost:8080");

		target = target.path("/codesign");

		Builder builder = target.request();

		Response response = builder.post(Entity.form(form));

		Assert.assertNotNull(response);

		Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());

		String postResponse = response.readEntity(String.class);

		Assert.assertEquals("ERROR: foo: no identity found", postResponse);
	}

	@Test
	public void testCodesignNoDmg() throws Exception {
		String tmpAppDir = _extractTestApp(
			"unsigned/LiferayWorkspace-1.5.0-osx-installer.app");

		MultivaluedHashMap<String, String> form = new MultivaluedHashMap<>();

		form.add(
			"identity", "Developer ID Application: Liferay, Inc. (7H3SPU5TB9)");
		form.add("path", tmpAppDir);

		Client client = _createClient();

		WebTarget target = client.target("http://localhost:8080");

		target = target.path("/codesign");

		Builder builder = target.request();

		Response response = builder.post(Entity.form(form));

		Assert.assertNotNull(response);

		Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());

		String postResponse = response.readEntity(String.class);

		Stream<String> stream = Stream.of(postResponse.split("\n"));

		Assert.assertTrue(
			stream.anyMatch(
				s -> s.contains("Developer ID Application: Liferay, Inc.")));

		Path tmpAppPath = Paths.get(tmpAppDir);

		Path codeSignaturePath = tmpAppPath.resolve("Contents/_CodeSignature");

		Assert.assertTrue(codeSignaturePath.toFile().exists());

		Path dmgPath = tmpAppPath.resolveSibling(
			"LiferayWorkspace-1.5.0-osx-installer.dmg");

		Assert.assertFalse(dmgPath.toFile().exists());
	}

	@Test
	public void testCodesignWithDmg() throws Exception {
		String tmpAppDir = _extractTestApp(
			"unsigned/LiferayWorkspace-1.5.0-osx-installer.app");

		MultivaluedHashMap<String, String> form = new MultivaluedHashMap<>();

		form.add(
			"identity", "Developer ID Application: Liferay, Inc. (7H3SPU5TB9)");
		form.add("path", tmpAppDir);
		form.add("dmg", "true");

		Client client = _createClient();

		WebTarget target = client.target("http://localhost:8080");

		target = target.path("/codesign");

		Builder builder = target.request();

		Response response = builder.post(Entity.form(form));

		Assert.assertNotNull(response);

		Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());

		String postResponse = response.readEntity(String.class);

		Stream<String> stream = Stream.of(postResponse.split("\n"));

		Assert.assertTrue(
			stream.anyMatch(
				s -> s.contains("Developer ID Application: Liferay, Inc.")));

		Path tmpAppPath = Paths.get(tmpAppDir);

		Path codeSignaturePath = tmpAppPath.resolve("Contents/_CodeSignature");

		Assert.assertTrue(codeSignaturePath.toFile().exists());

		Path dmgPath = tmpAppPath.resolveSibling(
			"LiferayWorkspace-1.5.0-osx-installer.dmg");

		Assert.assertTrue(dmgPath.toFile().exists());
	}

	@Test
	public void testDmg() throws Exception {
		String tmpAppDir = _extractTestApp(
			"signed/LiferayWorkspace-1.5.0-osx-installer.app");

		MultivaluedHashMap<String, String> form = new MultivaluedHashMap<>();

		form.add("path", tmpAppDir);

		Client client = _createClient();

		WebTarget target = client.target("http://localhost:8080");

		target = target.path("/dmg");

		Builder builder = target.request();

		Response response = builder.post(Entity.form(form));

		Assert.assertNotNull(response);

		Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());

		String postResponse = response.readEntity(String.class);

		Assert.assertEquals(
			"created: " + tmpAppDir.replaceAll("\\.app$", "\\.dmg"),
			postResponse);
	}

	@Test
	public void testVerifySigned() throws Exception {
		String tmpAppDir = _extractTestApp(
			"signed/LiferayWorkspace-1.5.0-osx-installer.app");

		Client client = _createClient();

		WebTarget target = client.target("http://localhost:8080");

		target = target.path("/verify");
		target = target.queryParam("path", tmpAppDir);

		Builder builder = target.request();

		Response response = builder.get(Response.class);

		Assert.assertNotNull(response);

		Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());

		String getResponse = response.readEntity(String.class);

		Stream<String> stream = Stream.of(getResponse.split("\n"));

		Assert.assertTrue(
			stream.anyMatch(
				s -> s.contains("Developer ID Application: Liferay, Inc.")));
	}

	@Test
	public void testVerifyUnSigned() throws Exception {
		String tmpAppDir = _extractTestApp(
			"unsigned/LiferayWorkspace-1.5.0-osx-installer.app");

		Client client = _createClient();

		WebTarget target = client.target("http://localhost:8080");

		target = target.path("/verify");
		target = target.queryParam("path", tmpAppDir);

		Builder builder = target.request();

		Response response = builder.get(Response.class);

		Assert.assertNotNull(response);

		Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());

		String getResponse = response.readEntity(String.class);

		Assert.assertTrue(
			getResponse,
			getResponse.contains("code object is not signed at all"));
	}

	private Client _createClient() {
		ClientBuilder clientBuilder;

		try {
			clientBuilder = _clientBuilderTracker.waitForService(5000);

			return clientBuilder.build();
		}
		catch (InterruptedException ie) {
			throw new RuntimeException(ie);
		}
	}

	private String _extractTestApp(String path) throws Exception {
		Path tmpDir = Files.createTempDirectory("temp-app-dir");

		Path srcPath = Paths.get("src/test/resources");

		File srcDir = srcPath.toFile();

		if (!srcDir.exists()) {
			srcPath = Paths.get("../../../").resolve(srcPath);

			srcDir = srcPath.toFile();
		}

		Assert.assertTrue(srcDir.getAbsolutePath(), srcDir.exists());

		IO.copy(srcDir, tmpDir.toFile());

		Path appPath = tmpDir.resolve(path);

		appPath = appPath.toAbsolutePath();

		Assert.assertTrue(appPath.toString(), appPath.toFile().exists());

		return appPath.toString();
	}

	private static final BundleContext _bundleContext = FrameworkUtil.getBundle(
		SigningApplicationTest.class).getBundleContext();

	private ServiceTracker<ClientBuilder, ClientBuilder> _clientBuilderTracker;

}