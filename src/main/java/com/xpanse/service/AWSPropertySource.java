package com.xpanse.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.EnumerablePropertySource;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParametersByPathRequest;
import software.amazon.awssdk.services.ssm.model.GetParametersByPathResponse;
import software.amazon.awssdk.services.ssm.model.Parameter;

import java.util.HashMap;
import java.util.Map;

public class AWSPropertySource extends EnumerablePropertySource<Map<String, Object>> {

    private static final Logger logger = LoggerFactory.getLogger(AWSPropertySource.class);

    private final Map<String, Object> properties = new HashMap<>();
    private final SsmClient ssmClient;
    private final SecretsManagerClient secretsManagerClient;
    private final String awsSecretsPrefix;
    private final String db_secret;

    // Constructor accepts 3 parameters: prefix for SSM, Secrets Manager prefix, and endpoint for Secrets Manager
    public AWSPropertySource(String ssmPrefix, String awsSecretsPrefix, String db_secret) {
        super("AWSPropertySource");
        this.ssmClient = SsmClient.create();
        this.secretsManagerClient = SecretsManagerClient.create();
        this.awsSecretsPrefix = awsSecretsPrefix;
        this.db_secret = db_secret;
        loadProperties(ssmPrefix);
    }

    private void loadProperties(String prefix) {
        String nextToken = null;

        do {
            GetParametersByPathRequest request = GetParametersByPathRequest.builder()
                    .path(prefix)
                    .recursive(true)
                    .withDecryption(true)
                    .nextToken(nextToken)
                    .build();
            GetParametersByPathResponse response = ssmClient.getParametersByPath(request);

            for (Parameter parameter : response.parameters()) {
                String key = parameter.name().replaceFirst(prefix, "");
                String value = parameter.value();
                logger.info("Fetched Parameter from SSM: Key = {}, Value = {}", key, value);
                properties.put(key, value);
                if (key.endsWith("_secret")) {
                    String db_secret = awsSecretsPrefix ;  // Construct the secret name
                    logger.info("Constructed Secret Name: {}", value);
                    value = getSecretValue(value);
                    if (value != null) {
                        JsonObject secretJson = JsonParser.parseString(value).getAsJsonObject();
                        setSecretInEnvVariable(secretJson);
                        System.out.println("secret Value:"+secretJson);
                        System.out.println("userName Value:"+ System.getProperty("DB_USERNAME"));

                    } else {
                        logger.error("Failed to fetch secret from Secrets Manager: {}", db_secret);
                    }
                }
            }

            nextToken = response.nextToken();

        } while (nextToken != null);
    }
    private String getSecretValue(String secretName) {
        try {
            GetSecretValueRequest request = GetSecretValueRequest.builder()
                    .secretId(secretName)
                    .build();

            GetSecretValueResponse response = secretsManagerClient.getSecretValue(request);

            String secretString = response.secretString();
            if (secretString == null) {
                logger.error("Secret value is null for secret: {}", secretName);
                return null;
            }
            return secretString;
        } catch (Exception e) {
            logger.error("Error fetching secret from Secrets Manager: {}", secretName, e);
            return null;
        }
    }
    private void setSecretInEnvVariable(JsonObject secretValue) {
        try {
            if (secretValue.has("username") && secretValue.get("username") != null) {
                System.setProperty("DB_USERNAME", secretValue.get("username").getAsString());
            } else {
                logger.error("Missing or null 'username' in secret value.");
            }

            if (secretValue.has("password") && secretValue.get("password") != null) {
                System.setProperty("DB_PASSWORD", secretValue.get("password").getAsString());

            } else {
                logger.error("Missing or null 'password' in secret value.");
            }

            if (secretValue.has("engine") && secretValue.get("engine") != null) {
                String engine = secretValue.get("engine").getAsString();
                System.setProperty("DB_ENGINE",  secretValue.get("engine").getAsString());

            } else {
                logger.error("Missing or null 'engine' in secret value.");
            }

            if (secretValue.has("host") && secretValue.get("host") != null) {
                String host = secretValue.get("host").getAsString();
                System.setProperty("DB_HOST", secretValue.get("host").getAsString());

            } else {
                logger.error("Missing or null 'host' in secret value.");
            }

            if (secretValue.has("port") && secretValue.get("port") != null) {
                String port = secretValue.get("port").getAsString();
                System.setProperty("DB_PORT", secretValue.get("port").getAsString());

            } else {
                logger.error("Missing or null 'port' in secret value.");
            }
            if (secretValue.has("dbClusterIdentifier") && secretValue.get("dbClusterIdentifier") != null) {
                String dbClusterIdentifier = secretValue.get("dbClusterIdentifier").getAsString();
                System.setProperty("DB_CLUSTER_IDENTIFIER", secretValue.get("dbClusterIdentifier").getAsString());

            } else {
                logger.error("Missing or null 'dbClusterIdentifier' in secret value.");
            }

        } catch (Exception e) {
            logger.error("Error while setting system properties from secret value", e);
        }
    }
    @Override
    public Object getProperty(String name) {
        return properties.get(name);
    }

    @Override
    public String[] getPropertyNames() {
        return properties.keySet().toArray(new String[0]);
    }

    public void close() {
        try {
            ssmClient.close();
            secretsManagerClient.close();
            logger.info("Closed SSM and Secrets Manager Clients successfully.");
        } catch (Exception e) {
            logger.error("Error closing clients", e);
        }
    }
}
