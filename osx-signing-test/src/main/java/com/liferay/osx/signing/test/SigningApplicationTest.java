package com.liferay.osx.signing.test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
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

import aQute.lib.io.IO;

public class SigningApplicationTest {

	private static final BundleContext bundleContext = FrameworkUtil.getBundle(SigningApplicationTest.class)
			.getBundleContext();

	private ServiceTracker<ClientBuilder, ClientBuilder> _clientBuilderTracker;

	@After
	public void after() {
		_clientBuilderTracker.close();
	}

	@Before
	public void before() {
		_clientBuilderTracker = new ServiceTracker<>(bundleContext, ClientBuilder.class, null);

		_clientBuilderTracker.open();
	}

	@Test
	public void testCodesignBadIdentity() throws Exception {
		Path tmpAppDir = _extractTestApp("unsigned/LiferayWorkspace-1.5.0-osx-installer.app");

		MultivaluedHashMap<String, String> form = new MultivaluedHashMap<String,String>();
		form.add("identity", "foo");
		form.add("path", tmpAppDir.toString());

		Response response = _createClient()
			.target("http://localhost:8888")
			.path("/codesign")
			.request()
			.post(Entity.form(form));

		Assert.assertNotNull(response);

		Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());

		String postResponse = response.readEntity(String.class);

		Assert.assertEquals("ERROR: foo: no identity found", postResponse);
	}

	@Test
	public void testVerifySigned() throws Exception {
		Path tmpAppDir = _extractTestApp("signed/LiferayWorkspace-1.5.0-osx-installer.app");

		Response response = _createClient()
			.target("http://localhost:8888")
			.path("/verify")
			.queryParam("path", tmpAppDir.toAbsolutePath().toString())
			.request()
			.get(Response.class);

		Assert.assertNotNull(response);

		Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());

		String getResponse = response.readEntity(String.class);

		Assert.assertTrue(Stream.of(getResponse.split("\n")).anyMatch(s -> s.contains("Developer ID Application: Liferay, Inc. (7H3SPU5TB9)")));
	}

	@Test
	public void testVerifyUnSigned() throws Exception {
		Path tmpAppDir = _extractTestApp("unsigned/LiferayWorkspace-1.5.0-osx-installer.app");

		Response response = _createClient()
			.target("http://localhost:8888")
			.path("/verify")
			.queryParam("path", tmpAppDir.toAbsolutePath().toString())
			.request()
			.get(Response.class);

		Assert.assertNotNull(response);

		Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());

		String getResponse = response.readEntity(String.class);

		Assert.assertTrue(getResponse, getResponse.contains("code object is not signed at all"));
	}

	@Test
	public void testCodesign() throws Exception {
		Path tmpAppDir = _extractTestApp("unsigned/LiferayWorkspace-1.5.0-osx-installer.app");

		MultivaluedHashMap<String, String> form = new MultivaluedHashMap<String,String>();
		form.add("identity", "Developer ID Application: Liferay, Inc. (7H3SPU5TB9)");
		form.add("path", tmpAppDir.toString());

		Response response = _createClient()
			.target("http://localhost:8888")
			.path("/codesign")
			.request()
			.post(Entity.form(form));

		Assert.assertNotNull(response);

		Assert.assertEquals(Status.OK.getStatusCode(), response.getStatus());

		String postResponse = response.readEntity(String.class);

		Assert.assertTrue(Stream.of(postResponse.split("\n")).anyMatch(s -> s.contains("Developer ID Application: Liferay, Inc. (7H3SPU5TB9)")));
	}

	private Path _extractTestApp(String path) throws Exception {
		Path tmpDir = Files.createTempDirectory("temp-app-dir");

		IO.copy(new File("src/test/resources"), tmpDir.toFile());

		Path appPath = tmpDir.resolve(path);

		Assert.assertTrue(appPath.toString(), appPath.toFile().exists());

		return appPath;
	}

	private Client _createClient() {
		ClientBuilder clientBuilder;

		try {
			clientBuilder = _clientBuilderTracker.waitForService(5000);

			return clientBuilder.build();
		} catch (InterruptedException ie) {
			throw new RuntimeException(ie);
		}
	}
}
