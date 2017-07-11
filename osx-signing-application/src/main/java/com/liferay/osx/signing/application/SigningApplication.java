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

package com.liferay.osx.signing.application;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import java.nio.file.Files;
import java.nio.file.Paths;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.jaxrs.whiteboard.JaxRSWhiteboardConstants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Gregory Amerson
 */
@Component(
	property = {
		JaxRSWhiteboardConstants.JAX_RS_APPLICATION_SELECT + "=(osgi.jaxrs.name=.default)",
		JaxRSWhiteboardConstants.JAX_RS_RESOURCE + "=true"
	},
	service = SigningApplication.class
)
public class SigningApplication {

	@Path("/codesign")
	@POST
	public String codesign(
		@FormParam("identity")String identity, @FormParam("path")String path) {

		_ensureBinariesAvailable();

		final File dir;

		try {
			dir = _getDir(path);
		}
		catch (Exception e) {
			_log.error(e.getMessage(), e);

			return "ERROR: " + e.getMessage();
		}

		try {
			_sign(dir, identity);

			List<String> output = _verify(dir);

			return _toString(output);
		}
		catch (Exception e) {
			_log.error("Error signing application", e);

			return "ERROR: " + e.getMessage();
		}
	}

	@GET
	@Path("/verify")
	public String verify(@QueryParam("path") String path) {
		_ensureBinariesAvailable();

		final File dir;

		try {
			dir = _getDir(path);
		}
		catch (Exception e) {
			_log.error(e.getMessage(), e);

			return "ERROR: " + e.getMessage();
		}

		try {
			List<String> output = _verify(dir);

			return _toString(output);
		}
		catch (Exception e) {
			_log.error("Error signing application", e);

			return "ERROR: " + e.getMessage();
		}
	}

	private void _ensureBinariesAvailable() {
		if (!_findExecutable("codesign")) {
			throw new RuntimeException("Could not find executable: codesign");
		}

		if (!_findExecutable("security")) {
			throw new RuntimeException("Could not find executable: security");
		}
	}

	private boolean _findExecutable(String exec) {
		String delimiter = Pattern.quote(File.pathSeparator);

		String[] paths = System.getenv("PATH").split(delimiter);

		Stream<String> stream = Stream.of(paths);

		Stream<java.nio.file.Path> map = stream.map(Paths::get);

		return map.anyMatch(path -> Files.exists(path.resolve(exec)));
	}

	private File _getDir(String path) throws Exception {
		File dir = new File(path);

		if (dir.exists() && dir.isDirectory()) {
			return dir;
		}

		throw new IllegalArgumentException(
			path + " is not an existing directory.");
	}

	private List<String> _readStreamFully(InputStream inputStream)
		throws IOException {

		List<String> retval = new ArrayList<>();

		InputStreamReader inputStreamReader = new InputStreamReader(
			inputStream);

		BufferedReader bufferedReader = new BufferedReader(inputStreamReader);

		String line;

		while ((line = bufferedReader.readLine()) != null) {
			retval.add(line);
		}

		return retval;
	}

	private List<String> _sign(File dir, String identity)
		throws InterruptedException, IOException {

		ProcessBuilder processBuilder = new ProcessBuilder();

		processBuilder.command(
			"codesign", "--deep", "-s", identity, dir.getAbsolutePath());

		Process signProcess = processBuilder.start();

		List<String> stderr = _readStreamFully(signProcess.getErrorStream());

		if (!stderr.isEmpty()) {
			throw new RuntimeException(_toString(stderr));
		}

		int exitCode = signProcess.waitFor();

		if (exitCode > 0) {
			String message = "codesign returned error: " + _toString(stderr);

			throw new RuntimeException(message);
		}

		return stderr;
	}

	private String _toString(List<String> strings) {
		return strings.stream().collect(Collectors.joining("\n"));
	}

	private List<String> _verify(File dir) throws Exception {
		ProcessBuilder processBuilder = new ProcessBuilder();

		processBuilder.command(
			"codesign", "-dv", "--verbose=4", dir.getAbsolutePath());

		Process verifyProcess = processBuilder.start();

		List<String> stderr = _readStreamFully(verifyProcess.getErrorStream());

		int exitCode = verifyProcess.waitFor();

		if (exitCode > 0) {
			String message = "codesign returned error\n: " + _toString(stderr);

			throw new RuntimeException(message);
		}

		return stderr;
	}

	private static final Logger _log = LoggerFactory.getLogger(
		SigningApplication.class);

}