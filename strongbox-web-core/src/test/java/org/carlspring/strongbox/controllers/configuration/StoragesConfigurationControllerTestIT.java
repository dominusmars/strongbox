package org.carlspring.strongbox.controllers.configuration;

import org.carlspring.strongbox.booters.PropertiesBooter;
import org.carlspring.strongbox.config.IntegrationTest;
import org.carlspring.strongbox.forms.configuration.MavenRepositoryConfigurationForm;
import org.carlspring.strongbox.forms.configuration.ProxyConfigurationForm;
import org.carlspring.strongbox.forms.configuration.RemoteRepositoryForm;
import org.carlspring.strongbox.forms.configuration.RepositoryForm;
import org.carlspring.strongbox.forms.configuration.StorageForm;
import org.carlspring.strongbox.providers.layout.Maven2LayoutProvider;
import org.carlspring.strongbox.rest.common.RestAssuredBaseTest;
import org.carlspring.strongbox.service.ProxyRepositoryConnectionPoolConfigurationService;
import org.carlspring.strongbox.storage.Storage;
import org.carlspring.strongbox.storage.StorageData;
import org.carlspring.strongbox.storage.repository.Repository;
import org.carlspring.strongbox.storage.repository.RepositoryData;
import org.carlspring.strongbox.yaml.configuration.repository.MavenRepositoryConfiguration;

import javax.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import org.apache.http.pool.PoolStats;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpServerErrorException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.carlspring.strongbox.controllers.configuration.StoragesConfigurationController.FAILED_SAVE_STORAGE_FORM_ERROR;
import static org.carlspring.strongbox.controllers.configuration.StoragesConfigurationController.SUCCESSFUL_REPOSITORY_REMOVAL;
import static org.carlspring.strongbox.controllers.configuration.StoragesConfigurationController.SUCCESSFUL_REPOSITORY_SAVE;
import static org.carlspring.strongbox.controllers.configuration.StoragesConfigurationController.SUCCESSFUL_SAVE_STORAGE;
import static org.carlspring.strongbox.controllers.configuration.StoragesConfigurationController.SUCCESSFUL_STORAGE_REMOVAL;
import static org.carlspring.strongbox.controllers.configuration.StoragesConfigurationController.SUCCESSFUL_UPDATE_STORAGE;
import static org.carlspring.strongbox.rest.client.RestAssuredArtifactClient.OK;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

/**
 * @author Pablo Tirado
 */
@IntegrationTest
public class StoragesConfigurationControllerTestIT
        extends RestAssuredBaseTest
{

    private static final String VALID_STORAGE_ID = "storage1";

    private static final String EXISTING_STORAGE_ID = STORAGE0;

    private RepositoryForm repositoryForm0;

    private RepositoryForm repositoryForm1;

    @Inject
    private PropertiesBooter propertiesBooter;

    @Inject
    private ProxyRepositoryConnectionPoolConfigurationService proxyRepositoryConnectionPoolConfigurationService;


    static ProxyConfigurationForm createProxyConfiguration()
    {
        ProxyConfigurationForm proxyConfiguration = new ProxyConfigurationForm();
        proxyConfiguration.setHost("localhost");
        proxyConfiguration.setPort(8080);
        proxyConfiguration.setUsername("user1");
        proxyConfiguration.setPassword("pass2");
        proxyConfiguration.setType("http");

        List<String> nonProxyHosts = Lists.newArrayList();
        nonProxyHosts.add("localhost");
        nonProxyHosts.add("some-hosts.com");
        proxyConfiguration.setNonProxyHosts(nonProxyHosts);

        return proxyConfiguration;
    }

    @Override
    @BeforeEach
    public void init()
            throws Exception
    {
        super.init();

        setContextBaseUrl("/api/configuration/strongbox/storages");

        repositoryForm0 = new RepositoryForm();
        repositoryForm0.setId("repository0");
        repositoryForm0.setAllowsRedeployment(true);
        repositoryForm0.setSecured(true);
        repositoryForm0.setLayout(Maven2LayoutProvider.ALIAS);
        repositoryForm0.setType("hosted");
        repositoryForm0.setPolicy("release");
        repositoryForm0.setImplementation("file-system");
        repositoryForm0.setStatus("In Service");

        repositoryForm1 = new RepositoryForm();
        repositoryForm1.setId("repository1");
        repositoryForm1.setAllowsForceDeletion(true);
        repositoryForm1.setTrashEnabled(true);
        repositoryForm1.setProxyConfiguration(createProxyConfiguration());
        repositoryForm1.setLayout(Maven2LayoutProvider.ALIAS);
        repositoryForm1.setType("hosted");
        repositoryForm1.setPolicy("release");
        repositoryForm1.setImplementation("file-system");
        repositoryForm1.setStatus("In Service");
        repositoryForm1.setGroupRepositories(ImmutableSet.of("repository0"));
    }

    private String getBaseDir(String storageId)
    {
        String directory = propertiesBooter.getStorageBooterBasedir() + "/" + storageId;

        return Paths.get(directory).toAbsolutePath().toString();
    }

    @Test
    public void testGetStorages()
    {
        String url = getContextBaseUrl();

        givenCustom().accept(MediaType.APPLICATION_JSON_VALUE)
                     .when()
                     .get(url)
                     .peek()
                     .then()
                     .statusCode(OK);
    }

    @Test
    public void testGetStorage()
    {
        String url = getContextBaseUrl() + "/" + EXISTING_STORAGE_ID;

        givenCustom().accept(MediaType.APPLICATION_JSON_VALUE)
                     .when()
                     .get(url)
                     .peek()
                     .then()
                     .statusCode(OK);
    }

    @Test
    public void testGetGroupRepository()
    {
        String url = getContextBaseUrl() + "/storage-common-proxies/group-common-proxies";

        givenCustom().accept(MediaType.APPLICATION_JSON_VALUE)
                     .when()
                     .get(url)
                     .peek()
                     .then()
                     .statusCode(OK);
    }

    @Test
    public void testGetMavenRepository()
    {
        String url = getContextBaseUrl() + "/" + EXISTING_STORAGE_ID + "/releases";

        givenCustom().accept(MediaType.APPLICATION_JSON_VALUE)
                     .when()
                     .get(url)
                     .peek()
                     .then()
                     .statusCode(OK);
    }

    @Test
    public void testCreateAndUpdateStorage()
    {
        final String storageId = VALID_STORAGE_ID;

        StorageForm storage1 = buildStorageForm(storageId);

        String url = getContextBaseUrl();

        logger.debug("Using storage class " + storage1.getClass().getName());

        // 1. Create storage
        givenCustom().contentType(MediaType.APPLICATION_JSON_VALUE)
                     .accept(MediaType.APPLICATION_JSON_VALUE)
                     .body(storage1)
                     .when()
                     .put(url)
                     .prettyPeek()
                     .then()
                     .statusCode(OK)
                     .body(containsString(SUCCESSFUL_SAVE_STORAGE));

        Storage storage = getStorage(storageId);

        assertThat(storage).as("Failed to get storage (" + storageId + ")!").isNotNull();
        assertThat(storage1.getBasedir()).isEqualTo(storage.getBasedir());

        // 2. Update storage.
        url = getContextBaseUrl() + "/" + storageId;
        String newBasedir = getBaseDir(storageId) + "-updated";
        storage1.setBasedir(newBasedir);

        givenCustom().contentType(MediaType.APPLICATION_JSON_VALUE)
                     .accept(MediaType.APPLICATION_JSON_VALUE)
                     .body(storage1)
                     .when()
                     .put(url)
                     .prettyPeek()
                     .then()
                     .statusCode(OK)
                     .body(containsString(SUCCESSFUL_UPDATE_STORAGE));

        storage = getStorage(storageId);

        assertThat(storage).as("Failed to get storage (" + storageId + ")!").isNotNull();
        assertThat(storage1.getBasedir())
                .as("Failed to update storage (" + storageId + ") basedir!")
                .isEqualTo(storage.getBasedir());
    }

    @ParameterizedTest
    @ValueSource(strings = { MediaType.APPLICATION_JSON_VALUE,
                             MediaType.TEXT_PLAIN_VALUE })
    public void testCreatingStorageWithExistingIdShouldFail(String acceptHeader)
    {
        StorageForm form = buildStorageForm(EXISTING_STORAGE_ID);

        String url = getContextBaseUrl();

        givenCustom().contentType(MediaType.APPLICATION_JSON_VALUE)
                     .accept(acceptHeader)
                     .body(form)
                     .when()
                     .put(url)
                     .peek()
                     .then()
                     .statusCode(HttpStatus.BAD_REQUEST.value())
                     .body(containsString(FAILED_SAVE_STORAGE_FORM_ERROR));
    }

    private StorageForm buildStorageForm(final String storageId)
    {
        final String basedir = getBaseDir(storageId);

        StorageForm form = new StorageForm();
        form.setId(storageId);
        form.setBasedir(basedir);

        return form;
    }

    @ParameterizedTest
    @ValueSource(strings = { "xml",
                             "org",
                             "com",
                             "pl",
                             "json" })
    public void testAddGetRepository(String extension)
    {
        final String storageId = EXISTING_STORAGE_ID;

        StorageForm storage0 = buildStorageForm(storageId);

        RepositoryForm repositoryForm0_1 = new RepositoryForm();
        repositoryForm0_1.setId("repository0_1_" + extension + "." + extension);
        repositoryForm0_1.setAllowsRedeployment(true);
        repositoryForm0_1.setSecured(true);
        repositoryForm0_1.setLayout(Maven2LayoutProvider.ALIAS);
        MavenRepositoryConfigurationForm mavenRepositoryConfigurationForm = new MavenRepositoryConfigurationForm();
        mavenRepositoryConfigurationForm.setIndexingEnabled(true);
        mavenRepositoryConfigurationForm.setIndexingClassNamesEnabled(false);
        mavenRepositoryConfigurationForm.setCronExpression("0 0 2 * * ?");
        repositoryForm0_1.setRepositoryConfiguration(mavenRepositoryConfigurationForm);
        repositoryForm0_1.setType("hosted");
        repositoryForm0_1.setPolicy("release");
        repositoryForm0_1.setImplementation("file-system");
        repositoryForm0_1.setStatus("In Service");
        Set<String> groupRepositories = new LinkedHashSet<>();
        String groupRepository1 = "maven-central";
        String groupRepository2 = "carlspring";
        groupRepositories.add(groupRepository1);
        groupRepositories.add(groupRepository2);
        repositoryForm0_1.setGroupRepositories(groupRepositories);

        Integer maxConnectionsRepository2 = 30;

        RepositoryForm repositoryForm0_2 = new RepositoryForm();
        repositoryForm0_2.setId("repository0_2_" + extension + "." + extension);
        repositoryForm0_2.setAllowsForceDeletion(true);
        repositoryForm0_2.setTrashEnabled(true);
        repositoryForm0_2.setProxyConfiguration(createProxyConfiguration());
        repositoryForm0_2.setLayout(Maven2LayoutProvider.ALIAS);
        repositoryForm0_2.setType("proxy");
        repositoryForm0_2.setPolicy("release");
        repositoryForm0_2.setImplementation("file-system");
        repositoryForm0_2.setStatus("In Service");
        repositoryForm0_2.setGroupRepositories(ImmutableSet.of("repository0"));
        repositoryForm0_2.setHttpConnectionPool(maxConnectionsRepository2);

        String secondRepositoryUrl = "http://abc.def";

        RemoteRepositoryForm remoteRepositoryForm = new RemoteRepositoryForm();
        remoteRepositoryForm.setUrl(secondRepositoryUrl);
        remoteRepositoryForm.setCheckIntervalSeconds(1000);
        repositoryForm0_2.setRemoteRepository(remoteRepositoryForm);

        addRepository(repositoryForm0_1, storage0);
        addRepository(repositoryForm0_2, storage0);

        Storage storage = getStorage(storageId);
        Repository repository0 = storage.getRepositories().get(repositoryForm0_1.getId());
        Repository repository1 = storage.getRepositories().get(repositoryForm0_2.getId());

        Set<String> groupRepositoriesMap = repository0.getGroupRepositories();
        Set<String> groupRepositoriesMapExpected = new LinkedHashSet<>();
        groupRepositoriesMapExpected.add(groupRepository1);
        groupRepositoriesMapExpected.add(groupRepository2);

        assertThat(storage)
                .as( "Failed to get storage (" + storageId + ")!")
                .isNotNull();
        assertThat(storage.getRepositories().isEmpty())
                .as("Failed to get storage (" + storageId + ")!")
                .isFalse();
        assertThat(repository0.allowsRedeployment())
                .as("Failed to get storage (" + storageId + ")!")
                .isTrue();
        assertThat(repository0.isSecured())
                .as("Failed to get storage (" + storageId + ")!")
                .isTrue();
        assertThat(repository0.getRepositoryConfiguration())
                .as("Failed to get storage (" + storageId + ")!")
                .isNotNull();
        assertThat(repository0.getRepositoryConfiguration())
                .as("Failed to get storage (" + storageId + ")!")
                .isInstanceOf(MavenRepositoryConfiguration.class);
        assertThat(((MavenRepositoryConfiguration) repository0.getRepositoryConfiguration()).isIndexingEnabled())
                .as("Failed to get storage (" + storageId + ")!")
                .isTrue();
        assertThat(((MavenRepositoryConfiguration) repository0.getRepositoryConfiguration()).isIndexingClassNamesEnabled())
                .as("Failed to get storage (" + storageId + ")!")
                .isFalse();
        assertThat(((MavenRepositoryConfiguration) repository0.getRepositoryConfiguration()).getCronExpression())
                .as("Failed to get storage(" + storageId + ")!")
                .isEqualTo("0 0 2 * * ?");
        assertThat(groupRepositoriesMap).isEqualTo(groupRepositoriesMapExpected);
        assertThat(repository1.allowsForceDeletion())
                .as("Failed to get storage (" + storageId + ")!")
                .isTrue();
        assertThat(repository1.isTrashEnabled())
                .as("Failed to get storage (" + storageId + ")!")
                .isTrue();
        assertThat(((RepositoryData) repository1).getProxyConfiguration().getHost())
                .as("Failed to get storage (" + storageId + ")!")
                .isNotNull();
        assertThat(((RepositoryData) repository1).getProxyConfiguration().getHost())
                .as("Failed to get storage (" + storageId + ")!")
                .isEqualTo("localhost");

        PoolStats poolStatsRepository2 = proxyRepositoryConnectionPoolConfigurationService.getPoolStats(
                secondRepositoryUrl);

        assertThat(poolStatsRepository2.getMax())
                .as("Max connections for proxy repository not set accordingly!")
                .isEqualTo(maxConnectionsRepository2);

        deleteRepository(storage0.getId(), repositoryForm0_1.getId());
        deleteRepository(storage0.getId(), repositoryForm0_2.getId());
    }

    @ParameterizedTest
    @ValueSource(strings = { "/xml",
                             "org/",
                             "c/om",
                             "p/l",
                             "/json" })
    public void disallowSlashesInRepositoryId(String extension)
    {
        final String storageId = EXISTING_STORAGE_ID;

        RepositoryForm repositoryForm0_1 = new RepositoryForm();
        repositoryForm0_1.setId("repository0_1_" + extension + "." + extension);
        repositoryForm0_1.setAllowsRedeployment(true);
        repositoryForm0_1.setSecured(true);
        repositoryForm0_1.setLayout(Maven2LayoutProvider.ALIAS);
        MavenRepositoryConfigurationForm mavenRepositoryConfigurationForm = new MavenRepositoryConfigurationForm();
        mavenRepositoryConfigurationForm.setIndexingEnabled(true);
        mavenRepositoryConfigurationForm.setIndexingClassNamesEnabled(false);
        repositoryForm0_1.setRepositoryConfiguration(mavenRepositoryConfigurationForm);
        repositoryForm0_1.setType("hosted");
        repositoryForm0_1.setPolicy("release");
        repositoryForm0_1.setImplementation("file-system");
        repositoryForm0_1.setStatus("In Service");
        Set<String> groupRepositories = new LinkedHashSet<>();
        String groupRepository1 = "maven-central";
        String groupRepository2 = "carlspring";
        groupRepositories.add(groupRepository1);
        groupRepositories.add(groupRepository2);
        repositoryForm0_1.setGroupRepositories(groupRepositories);

        String url = getContextBaseUrl() + "/" + storageId + "/" + repositoryForm0_1.getId();

        givenCustom().contentType(MediaType.APPLICATION_JSON_VALUE)
                     .accept(MediaType.APPLICATION_JSON_VALUE)
                     .body(repositoryForm0_1)
                     .when()
                     .put(url)
                     .then()
                     .statusCode(not(equalTo(OK)));
    }

    @Test
    public void testUpdatingRepositoryWithNonExistingStorage()
    {
        String url = getContextBaseUrl() + "/non-existing-storage/fake-repository";
        RepositoryForm form = new RepositoryForm();

        givenCustom().contentType(MediaType.APPLICATION_JSON_VALUE)
                     .accept(MediaType.APPLICATION_JSON_VALUE)
                     .body(form)
                     .when()
                     .put(url)
                     .peek()
                     .then()
                     .statusCode(404);
    }

    private Storage getStorage(String storageId)
    {
        String url = getContextBaseUrl() + "/" + storageId;

        return givenCustom().accept(MediaType.APPLICATION_JSON_VALUE)
                            .when()
                            .get(url)
                            .prettyPeek()
                            .as(StorageData.class);
    }

    private void addRepository(RepositoryForm repository,
                               final StorageForm storage)
    {
        String url;
        if (repository == null)
        {
            logger.error("Unable to add non-existing repository.");

            throw new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR,
                                               "Unable to add non-existing repository.");
        }

        if (storage == null)
        {
            logger.error("Storage associated with repo is null.");

            throw new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR,
                                               "Storage associated with repo is null.");
        }

        try
        {
            url = getContextBaseUrl() + "/" + storage.getId() + "/" + repository.getId();

            givenCustom().contentType(MediaType.APPLICATION_JSON_VALUE)
                         .accept(MediaType.APPLICATION_JSON_VALUE)
                         .body(repository)
                         .when()
                         .put(url)
                         .then()
                         .statusCode(OK)
                         .body(containsString(SUCCESSFUL_REPOSITORY_SAVE));

        }
        catch (RuntimeException e)
        {
            logger.error("Unable to create web resource.", e);

            throw new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private void deleteRepository(String storageId,
                                  String repositoryId)
    {
        String url = String.format("%s/%s/%s?force=%s", getContextBaseUrl(), storageId, repositoryId, true);

        givenCustom().contentType(MediaType.APPLICATION_JSON_VALUE)
                     .accept(MediaType.APPLICATION_JSON_VALUE)
                     .when()
                     .delete(url)
                     .then()
                     .statusCode(OK)
                     .body(containsString(SUCCESSFUL_REPOSITORY_REMOVAL));

        String repoDir = getBaseDir(storageId) + "/" + repositoryId;

        assertThat(Files.exists(Paths.get(repoDir))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = { "/xml",
                             "org/",
                             "c/om",
                             "p/l",
                             "/json" })
    public void disallowSlashesInStorageId(String extension)
    {
        final String storageId = "storage_" + extension + "." + extension;

        StorageForm storage2 = buildStorageForm(storageId);

        String url = getContextBaseUrl();

        // 1. Create storage.
        givenCustom().contentType(MediaType.APPLICATION_JSON_VALUE)
                     .accept(MediaType.APPLICATION_JSON_VALUE)
                     .body(storage2)
                     .when()
                     .put(url)
                     .peek() // Use peek() to print the output
                     .then()
                     .statusCode(HttpStatus.BAD_REQUEST.value())
                     .body(containsString("must match \\\"[a-zA-Z0-9\\\\-\\\\_\\\\.]+\\\""));
    }

    @ParameterizedTest
    @ValueSource(strings = { "xml",
                             "org",
                             "com",
                             "pl",
                             "json" })
    public void testCreateAndDeleteStorage(String extension)
    {
        final String storageId = "storage2_" + extension + "." + extension;
        final String repositoryId0 = repositoryForm0.getId();
        final String repositoryId1 = repositoryForm1.getId();

        StorageForm storage2 = buildStorageForm(storageId);

        String url = getContextBaseUrl();

        // 1. Create storage.
        givenCustom().contentType(MediaType.APPLICATION_JSON_VALUE)
                     .accept(MediaType.APPLICATION_JSON_VALUE)
                     .body(storage2)
                     .when()
                     .put(url)
                     .peek() // Use peek() to print the output
                     .then()
                     .statusCode(OK)
                     .body(containsString(SUCCESSFUL_SAVE_STORAGE));

        // 1.1. Add repositories to storage.
        addRepository(repositoryForm0, storage2);
        addRepository(repositoryForm1, storage2);

        url = "/api/configuration/strongbox/proxy-configuration";

        // 2. Check proxy configuration from storage created.
        givenCustom().accept(MediaType.APPLICATION_JSON_VALUE)
                     .params("storageId", storageId, "repositoryId", repositoryId1)
                     .when()
                     .get(url)
                     .peek() // Use peek() to print the output
                     .then()
                     .statusCode(OK);

        Storage storage = getStorage(storageId);

        assertThat(storage).as("Failed to get storage (" + storageId + ")!").isNotNull();
        assertThat(storage.getRepositories().isEmpty()).as("Failed to get storage (" + storageId + ")!").isFalse();

        url = getContextBaseUrl() + "/" + storageId;

        logger.debug(url);

        // 3. Delete storage created.
        givenCustom().contentType(MediaType.TEXT_PLAIN_VALUE)
                     .accept(MediaType.TEXT_PLAIN_VALUE)
                     .param("force", true)
                     .when()
                     .delete(url)
                     .peek() // Use peek() to print the output
                     .then()
                     .statusCode(OK)
                     .body(containsString(SUCCESSFUL_STORAGE_REMOVAL));

        url = getContextBaseUrl() + "/" + storageId;

        logger.debug(storageId);
        logger.debug(repositoryId0);

        // 4. Check that the storage deleted does not exist anymore.
        givenCustom().contentType(MediaType.TEXT_PLAIN_VALUE)
                     .when()
                     .get(url)
                     .peek() // Use peek() to print the output
                     .then()
                     .statusCode(HttpStatus.NOT_FOUND.value());
    }

    @ParameterizedTest
    @ValueSource(strings = { "xml",
                             "org",
                             "com",
                             "pl",
                             "json" })
    public void whenStorageIsCreatedWithoutBasedirProvidedDefaultIsSet(String extension)
    {
        final String storageId = "storage4_" + extension + "." + extension;

        StorageForm storage4 = buildStorageForm(storageId);
        storage4.setBasedir(null);

        String url = getContextBaseUrl();

        // 1. Create storage without base dir provided.
        givenCustom().contentType(MediaType.APPLICATION_JSON_VALUE)
                     .accept(MediaType.APPLICATION_JSON_VALUE)
                     .body(storage4)
                     .when()
                     .put(url)
                     .peek() // Use peek() to print the output
                     .then()
                     .statusCode(OK)
                     .body(containsString(SUCCESSFUL_SAVE_STORAGE));

        Storage storage = getStorage(storageId);
        assertThat(storage).as("Failed to get storage (" + storageId + ")!").isNotNull();

        // Storage basedir will be created only when repository created.
        addRepository(repositoryForm0, storage4);

        // 2. Confirm default base dir has been created
        String storageBaseDir = getBaseDir(storageId);
        assertThat(Files.exists(Paths.get(storageBaseDir))).isTrue();

        url = getContextBaseUrl() + "/" + storageId;

        // 3. Delete storage created.
        givenCustom().contentType(MediaType.TEXT_PLAIN_VALUE)
                     .accept(MediaType.TEXT_PLAIN_VALUE)
                     .param("force", true)
                     .when()
                     .delete(url)
                     .peek() // Use peek() to print the output
                     .then()
                     .statusCode(OK)
                     .body(containsString(SUCCESSFUL_STORAGE_REMOVAL));

        // 4. Check that the storage deleted does not exist anymore.
        givenCustom().contentType(MediaType.TEXT_PLAIN_VALUE)
                     .when()
                     .get(url)
                     .peek() // Use peek() to print the output
                     .then()
                     .statusCode(HttpStatus.NOT_FOUND.value());

        // 5. Confirm base dir has been deleted
        assertThat(Files.exists(Paths.get(storageBaseDir))).isFalse();
    }

}
