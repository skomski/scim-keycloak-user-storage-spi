/*
 * Copyright 2021 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package keycloak.scim_user_spi;

import java.io.IOException;

import org.apache.http.HttpStatus;
import org.jboss.logging.Logger;
import org.keycloak.broker.provider.util.SimpleHttp;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.storage.UserStorageProvider;
import org.keycloak.storage.user.UserRegistrationProvider;

import keycloak.scim_user_spi.schemas.SCIMError;
import keycloak.scim_user_spi.schemas.SCIMUser;

/**
 * @author <a href="mailto:jstephen@redhat.com">Justin Stephenson</a>
 * @version $Revision: 1 $
 */
public class SCIMUserStorageProvider implements
		UserStorageProvider,
		UserRegistrationProvider {
	protected KeycloakSession session;
	protected ComponentModel model;
	protected Scim scim;
	private static final Logger logger = Logger.getLogger(SCIMUserStorageProvider.class);

	public SCIMUserStorageProvider(KeycloakSession session, ComponentModel model, Scim scim) {
		this.session = session;
		this.model = model;
		this.scim = scim;
	}

	@Override
	public void close() {

	}

	// UserRegistrationProvider methods
	@Override
	public UserModel addUser(RealmModel realm, String username) {
		Scim scim = this.scim;

		SimpleHttp.Response resp = scim.createUser(username);
		SCIMUser.Resource scim_user;

		try {
			if (resp.getStatus() != HttpStatus.SC_CREATED) {
				logger.warn("Unexpected create status code returned");
				SCIMError error = resp.asJson(SCIMError.class);
				logger.warn(error.getDetail());
				resp.close();
				return null;
			}
			scim_user = resp.asJson(SCIMUser.Resource.class);
			resp.close();
		} catch (IOException e) {
			logger.errorv("Error: {0}", e.getMessage());
			throw new RuntimeException(e);
		}

		UserModel user = session.userLocalStorage().addUser(realm, username);
		user.setSingleAttribute("SCIM_ID", scim_user.getId());
		user.setFederationLink(model.getId());

		logger.infov("Creating SCIM user {0} in keycloak", username);
		return new SCIMUserModelDelegate(user, model);
	}

	@Override
	public boolean removeUser(RealmModel realm, UserModel user) {
		logger.infov("Removing user: {0}", user.getUsername());
		Scim scim = this.scim;

		SimpleHttp.Response resp = scim.deleteUser(user.getFirstAttribute("SCIM_ID"));
		Boolean status = false;
		try {
			status = resp.getStatus() == HttpStatus.SC_NO_CONTENT;
			resp.close();
		} catch (IOException e) {
			logger.errorv("Error: {0}", e.getMessage());
			throw new RuntimeException(e);
		}
		return status;
	}
}
