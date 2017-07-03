package com.liferay.osx.signing.application;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
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
	public String codeSign(@PathParam("name") String appPath) {
		if (_log.isDebugEnabled()) {
			_log.debug("URI: " + _uriInfo.getAbsolutePath());
		}

		return "codesign: appPath=" + appPath;
	}

	@Context
	UriInfo _uriInfo;

	private static final Logger _log = LoggerFactory.getLogger(SigningApplication.class);

}