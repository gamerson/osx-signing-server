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
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.jaxrs.whiteboard.JaxRSWhiteboardConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(
	property = {
		JaxRSWhiteboardConstants.JAX_RS_APPLICATION_SELECT + "=(osgi.jaxrs.name=.default)",
		JaxRSWhiteboardConstants.JAX_RS_RESOURCE + "=true"
	},
	service = SigningApplication.class
)
public class SigningApplication {

	@POST
	@Path("/codesign")
	public String codesign(@FormParam("identity")String identity, @FormParam("path")String path) {
		_ensureBinariesAvailable();

		final File dir;

		try {
			dir = _getDir(path);
		} catch (Exception e) {
			_log.error(e.getMessage(), e);

			return "ERROR: " + e.getMessage();
		}

		try {
			_sign(dir, identity);

			List<String> output = _verify(dir);

			return _toString(output);
		} catch (Exception e) {
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
		} catch (Exception e) {
			_log.error(e.getMessage(), e);

			return "ERROR: " + e.getMessage();
		}

		try {
			List<String> output = _verify(dir);

			return _toString(output);
		} catch (Exception e) {
			_log.error("Error signing application", e);

			return "ERROR: " + e.getMessage();
		}
	}

	private List<String> _sign(File dir, String identity) throws IOException, InterruptedException {
		// codesign --deep -s "Developer ID Application: Liferay, Inc." -f DeveloperStudio.app/

		Process signProcess = new ProcessBuilder().
			command("codesign", "--deep", "-s", identity, dir.getAbsolutePath()).
			start();

		List<String> stderr = _readStreamFully(signProcess.getErrorStream());

		if (stderr.size() > 0) {
			throw new RuntimeException(_toString(stderr));
		}

	    int exitCode = signProcess.waitFor();

	    if (exitCode > 0) {
	    	throw new RuntimeException("codesign returned error code: " + _toString(stderr));
	    }

	    return stderr;
	}

	private List<String> _verify(File dir) throws Exception {
		// codesign -dv --verbose=4 DeveloperStudio.app/

		Process verifyProcess = new ProcessBuilder().
				command("codesign", "-dv", "--verbose=4", dir.getAbsolutePath()).
				start();

		List<String> stderr = _readStreamFully(verifyProcess.getErrorStream());

	    int exitCode = verifyProcess.waitFor();

	    if (exitCode > 0) {
	    	throw new RuntimeException("codesign returned error\n: " + _toString(stderr));
	    }

	    return stderr;
	}

	private String _toString(List<String> strings) {
		return strings.stream().collect(Collectors.joining("\n"));
	}

	private List<String> _readStreamFully(InputStream inputStream) throws IOException {
		List<String> retval = new ArrayList<>();

		InputStreamReader isr = new InputStreamReader(inputStream);
	    BufferedReader br = new BufferedReader(isr);

	    String line;

	    while ((line = br.readLine()) != null) {
	    	retval.add(line);
	    }

		return retval;
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
		return Stream.of(System.getenv("PATH").split(Pattern.quote(File.pathSeparator)))
	        .map(Paths::get)
	        .anyMatch(path -> Files.exists(path.resolve(exec)));
	}


	private File _getDir(String path) throws Exception {
		File dir = new File(path);

		if (dir.exists() && dir.isDirectory()) {
			return dir;
		}

		throw new IllegalArgumentException(path + " is not an existing directory.");
	}

	@Context
	UriInfo _uriInfo;

	private static final Logger _log = LoggerFactory.getLogger(SigningApplication.class);

}