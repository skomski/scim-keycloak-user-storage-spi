package keycloak.scim_user_spi;

import java.util.ArrayList;
import java.util.List;

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.jboss.logging.Logger;
import org.keycloak.broker.provider.util.SimpleHttp;
import org.keycloak.component.ComponentModel;

import keycloak.scim_user_spi.schemas.SCIMUser;

public class Scim {
	private static final Logger logger = Logger.getLogger(Scim.class);

	private ComponentModel model;
	public static final String SCHEMA_CORE_USER = "urn:ietf:params:scim:schemas:core:2.0:User";
	public static final String SCHEMA_API_MESSAGES_SEARCHREQUEST = "urn:ietf:params:scim:api:messages:2.0:SearchRequest";

	CloseableHttpClient httpclient;

	public Scim(ComponentModel model) {
		this.model = model;

		CredentialsProvider provider = new BasicCredentialsProvider();
		UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(
				model.getConfig().getFirst("loginusername"), model.getConfig().getFirst("loginpassword"));
		provider.setCredentials(AuthScope.ANY, credentials);
		CloseableHttpClient httpclient = HttpClients.custom()
				.setDefaultCredentialsProvider(provider)
				.build();
		this.httpclient = httpclient;
	}

	public <T> SimpleHttp.Response clientRequest(String endpoint, String method, T entity) throws Exception {
		SimpleHttp.Response response = null;

		/* Build URL */
		String server = model.getConfig().getFirst("scimurl");
		String endpointurl = String.format("%s/%s", server, endpoint);

		logger.infov("Sending {0} request to {1}", method.toString(), endpointurl);

		try {
			switch (method) {
				case "GET":
					response = SimpleHttp.doGet(endpointurl, this.httpclient).asResponse();
					break;
				case "DELETE":
					response = SimpleHttp.doDelete(endpointurl, this.httpclient).asResponse();
					break;
				case "POST":
					response = SimpleHttp.doPost(endpointurl, this.httpclient).json(entity).asResponse();
					break;
				case "PUT":
					response = SimpleHttp.doPut(endpointurl, this.httpclient).json(entity).asResponse();
					break;
				default:
					logger.warn("Unknown HTTP method, skipping");
					break;
			}
		} catch (Exception e) {
			throw new Exception();
		}

		/* Caller is responsible for executing .close() */
		return response;
	}

	private SCIMUser.Resource getUserById(String id) {
		String userIdUrl = String.format("Users/%s", id);
		SCIMUser.Resource user;

		SimpleHttp.Response response;
		try {
			response = clientRequest(userIdUrl, "GET", null);
			user = response.asJson(SCIMUser.Resource.class);
			response.close();
		} catch (Exception e) {
			logger.errorv("Error: {0}", e.getMessage());
			throw new RuntimeException(e);
		}

		return user;
	}

	public SimpleHttp.Response deleteUser(String id) {
		String userIdUrl = String.format("Users/%s", id);

		SimpleHttp.Response response;
		try {
			response = clientRequest(userIdUrl, "DELETE", null);
		} catch (Exception e) {
			logger.errorv("Error: {0}", e.getMessage());
			throw new RuntimeException(e);
		}

		return response;
	}

	/*
	 * Keycloak UserRegistrationProvider addUser() method only provides username as
	 * input
	 */
	private SCIMUser.Resource setupUser(String username) {
		SCIMUser.Resource user = new SCIMUser.Resource();

		user.setSchemas(List.of(SCHEMA_CORE_USER));
		user.setUserName(username);
		user.setActive(true);

		return user;
	}

	public SimpleHttp.Response createUser(String username) {
		String usersUrl = "Users";

		SCIMUser.Resource newUser = setupUser(username);

		SimpleHttp.Response response;
		try {
			response = clientRequest(usersUrl, "POST", newUser);
		} catch (Exception e) {
			logger.errorv("Error: {0}", e.getMessage());
			return null;
		}

		return response;
	}

	private void setUserAttr(SCIMUser.Resource user, String attr, String value) {
		SCIMUser.Resource.Name name = user.getName();
		SCIMUser.Resource.Email email = new SCIMUser.Resource.Email();
		List<SCIMUser.Resource.Email> emails = new ArrayList<SCIMUser.Resource.Email>();

		switch (attr) {
			case "firstName":
				name.setGivenName(value);
				user.setName(name);
				break;
			case "lastName":
				name.setFamilyName(value);
				user.setName(name);
				break;
			case "email":
				email.setValue(value);
				emails.add(email);
				user.setEmails(emails);
				break;
			case "userName":
				user.setUserName(value);
				break;
			default:
				logger.info("Unknown user attribute to set: " + attr);
				break;
		}
	}

	public SimpleHttp.Response updateUser(String id, String attr, List<String> values) {
		logger.info(String.format("Updating %s attribute for %s", attr, id));

		SCIMUser.Resource user = getUserById(id);

		/* Modify attributes */
		setUserAttr(user, attr, values.get(0));

		/* Update user in SCIM */
		String modifyUrl = String.format("Users/%s", user.getId());

		SimpleHttp.Response response;
		try {
			response = clientRequest(modifyUrl, "PUT", user);
		} catch (Exception e) {
			logger.errorv("Error: {0}", e.getMessage());
			throw new RuntimeException(e);
		}

		return response;
	}

	public boolean getActive(SCIMUser user) {
		return Boolean.valueOf(user.getResources().get(0).getActive());
	}

	public String getEmail(SCIMUser user) {
		return user.getResources().get(0).getEmails().get(0).getValue();
	}

	public String getFirstName(SCIMUser user) {
		return user.getResources().get(0).getName().getGivenName();
	}

	public String getLastName(SCIMUser user) {
		return user.getResources().get(0).getName().getFamilyName();
	}

	public String getUserName(SCIMUser user) {
		return user.getResources().get(0).getUserName();
	}

	public String getId(SCIMUser user) {
		return user.getResources().get(0).getId();
	}
}
