/*
 * Copyright (C) 2016 Bonitasoft S.A.
 * Bonitasoft, 32 rue Gustave Eiffel - 38000 Grenoble
 * This library is free software; you can redistribute it and/or modify it under the terms
 * of the GNU Lesser General Public License as published by the Free Software Foundation
 * version 2.1 of the License.
 * This library is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License along with this
 * program; if not, write to the Free Software Foundation, Inc., 51 Franklin Street, Fifth
 * Floor, Boston, MA 02110-1301, USA.
 */
package org.bonitasoft.platform.setup;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import javax.naming.NamingException;
import javax.sql.DataSource;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.bonitasoft.platform.configuration.ConfigurationService;
import org.bonitasoft.platform.configuration.exception.PlatformConfigurationException;
import org.bonitasoft.platform.configuration.impl.ConfigurationServiceImpl;
import org.bonitasoft.platform.configuration.model.BonitaConfiguration;
import org.bonitasoft.platform.configuration.type.ConfigurationType;
import org.bonitasoft.platform.version.VersionService;
import org.bonitasoft.platform.version.impl.VersionServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Class that setup an environment for the engine to start on.
 * <p/>
 * It creates tables and insert the default configuration
 *
 * @author Baptiste Mesta
 */
@Component
public class PlatformSetup {

    public static final String BONITA_SETUP_FOLDER = "org.bonitasoft.platform.setup.folder";

    public static final String BONITA_SETUP_ACTION = "org.bonitasoft.platform.setup.action";

    private final static Logger LOGGER = LoggerFactory.getLogger(PlatformSetup.class);

    public static final String PLATFORM_CONF_FOLDER_NAME = "platform_conf";

    @Autowired
    private ScriptExecutor scriptExecutor;

    @Autowired
    private ConfigurationService configurationService;

    @Autowired
    private VersionService versionService;

    @Value("${db.vendor}")
    private String dbVendor;

    @Autowired
    private DataSource dataSource;

    private Path initialConfigurationFolder;
    private Path currentConfigurationFolder;
    private Path licensesFolder;

    public PlatformSetup(String dbVendor) {
        this.dbVendor = dbVendor;
    }

    public PlatformSetup() {
    }

    public void setDataSource(String driverClassName, String username, String password, String url) {
        dataSource = new DataSourceBuilder(PlatformSetup.class.getClassLoader())
                .driverClassName(driverClassName)
                .username(username)
                .password(password)
                .url(url)
                .build();
    }

    /**
     * Entry point that create the tables and insert the default configuration
     *
     * @throws PlatformSetupException
     */
    public void init() throws PlatformSetupException {
        initPlatformSetup();
        if (isPlatformAlreadyCreated()) {
            LOGGER.info("Platform is already created. Nothing to do.");
            return;
        } else {
            initializePlatform();
            LOGGER.info("Platform created.");
            push(initialConfigurationFolder);
            LOGGER.info("Initial configuration successfully pushed to database from folder " + initialConfigurationFolder);
        }

    }

    boolean isPlatformAlreadyCreated() {
        return scriptExecutor.isPlatformAlreadyCreated();
    }

    private void push(Path folderToPush) throws PlatformSetupException {
        if (!isPlatformAlreadyCreated()) {
            throw new PlatformSetupException("Platform is not created. run platform setup before pushing configuration.");
        }
        clean();
        if (Files.isDirectory(folderToPush)) {
            LOGGER.info("Pushing configuration from folder:" + folderToPush.toString());
            pushConfigurationFromSetupFolder(folderToPush);
            pushLicenses();
        } else {
            LOGGER.info("Folder :" + folderToPush.toAbsolutePath() + " does not exist, using classpath.");
            //TODO must be kept in order to not have everything broken, but remove that after changes on other modules
            pushConfigurationFromClassPath();
        }
    }

    void clean() {
        configurationService.deleteAllConfiguration();
    }

    /**
     * push all configuration files and licenses
     *
     * @throws PlatformSetupException
     */
    public void push() throws PlatformSetupException, PlatformConfigurationException {
        initPlatformSetup();
        checkPlatformVersion();
        push(currentConfigurationFolder);
        LOGGER.info("New configuration successfully pushed to database. You can now restart Bonita BPM to reflect your changes.");
    }

    /**
     * Entry point to retrieve all configuration files and write them to folder
     * each file will be located under sub folder according to its purpose. See {@link org.bonitasoft.platform.configuration.type.ConfigurationType} for all
     * available values
     * For tenant specific files, a tenants/[TENANT_ID] folder is created prior to configuration type
     *
     * @throws PlatformSetupException
     */
    void pull() throws PlatformConfigurationException, PlatformSetupException {
        initPlatformSetup();
        LOGGER.info("Pulling configuration into folder " + currentConfigurationFolder);
        pull(this.currentConfigurationFolder);
        LOGGER.info("Configuration successfully pulled into folder " + currentConfigurationFolder
                + ". You can now edit the configuration files and push the changes to update the platform");
    }

    public void pull(Path destinationFolder) throws PlatformConfigurationException, PlatformSetupException {
        checkPlatformVersion();
        try {
            if (Files.exists(destinationFolder)) {
                FileUtils.deleteDirectory(destinationFolder.toFile());
            }
            Files.createDirectories(destinationFolder);
            configurationService.writeAllConfigurationToFolder(destinationFolder.toFile());
        } catch (IOException e) {
            throw new PlatformConfigurationException(e);
        }
    }

    private void checkPlatformVersion() throws PlatformSetupException {
        if (!versionService.isValidPlatformVersion()) {
            throw new PlatformSetupException(new StringBuilder().append("Platform version [").append(versionService.getPlatformVersion())
                    .append("] is not supported by current platform setup version [").append(versionService.getPlatformSetupVersion()).append("]").toString());
        }
    }

    /**
     * lookup for license file and push them to database
     *
     * @throws PlatformSetupException
     */
    private void pushLicenses() throws PlatformSetupException {
        LOGGER.info("Pushing license files using license folder:" + licensesFolder.toString());
        if (Files.isDirectory(licensesFolder)) {
            try {
                configurationService.storeLicenses(licensesFolder.toFile());
            } catch (PlatformConfigurationException e) {
                throw new PlatformSetupException(e);
            }
        } else {
            LOGGER.info("Folder does not exists, no licenses pushed");
        }
    }

    private void initializePlatform() throws PlatformSetupException {
        scriptExecutor.createAndInitializePlatformIfNecessary();
    }

    private void initProperties() {
        if (dbVendor == null) {
            dbVendor = System.getProperty("sysprop.bonita.db.vendor");
        }
        String setupFolderPath = System.getProperty(BONITA_SETUP_FOLDER);
        Path platformConfFolder;
        if (setupFolderPath != null) {
            LOGGER.info("System property " + BONITA_SETUP_FOLDER + " is set to " + setupFolderPath);
            platformConfFolder = Paths.get(setupFolderPath).resolve(PLATFORM_CONF_FOLDER_NAME);
        } else {
            platformConfFolder = Paths.get(PLATFORM_CONF_FOLDER_NAME);
        }
        initializeFoldersPaths(platformConfFolder);
    }

    private void initializeFoldersPaths(Path platformConfFolder) {
        initialConfigurationFolder = platformConfFolder.resolve("initial");
        currentConfigurationFolder = platformConfFolder.resolve("current");
        licensesFolder = platformConfFolder.resolve("licenses");
    }

    private void pushConfigurationFromSetupFolder(Path folderToPush) throws PlatformSetupException {
        try {
            configurationService.storeAllConfiguration(folderToPush.toFile());
        } catch (PlatformConfigurationException e) {
            throw new PlatformSetupException(e);
        }

    }

    private void pushConfigurationFromClassPath() throws PlatformSetupException {
        try {
            List<BonitaConfiguration> platformInitConfigurations = new ArrayList<>();
            addIfExists(platformInitConfigurations, ConfigurationType.PLATFORM_INIT_ENGINE, "bonita-platform-init-community-custom.properties");
            addIfExists(platformInitConfigurations, ConfigurationType.PLATFORM_INIT_ENGINE, "bonita-platform-init-custom.xml");
            configurationService.storePlatformInitEngineConf(platformInitConfigurations);

            List<BonitaConfiguration> platformConfigurations = new ArrayList<>();
            addIfExists(platformConfigurations, ConfigurationType.PLATFORM_ENGINE, "bonita-platform-community-custom.properties");
            addIfExists(platformConfigurations, ConfigurationType.PLATFORM_ENGINE, "bonita-platform-custom.xml");
            //SP
            addIfExists(platformConfigurations, ConfigurationType.PLATFORM_ENGINE, "bonita-platform-private-community.properties");
            addIfExists(platformConfigurations, ConfigurationType.PLATFORM_ENGINE, "bonita-platform-sp-custom.properties");
            addIfExists(platformConfigurations, ConfigurationType.PLATFORM_ENGINE, "bonita-platform-sp-cluster-custom.properties");
            addIfExists(platformConfigurations, ConfigurationType.PLATFORM_ENGINE, "bonita-platform-sp-custom.xml");
            addIfExists(platformConfigurations, ConfigurationType.PLATFORM_ENGINE, "bonita-platform-hibernate-cache.xml");
            addIfExists(platformConfigurations, ConfigurationType.PLATFORM_ENGINE, "bonita-tenant-hibernate-cache.xml");
            configurationService.storePlatformEngineConf(platformConfigurations);

            List<BonitaConfiguration> tenantTemplateConfigurations = new ArrayList<>();
            addIfExists(tenantTemplateConfigurations, ConfigurationType.TENANT_TEMPLATE_ENGINE, "bonita-tenant-community-custom.properties");
            addIfExists(tenantTemplateConfigurations, ConfigurationType.TENANT_TEMPLATE_ENGINE, "bonita-tenants-custom.xml");
            //SP
            addIfExists(tenantTemplateConfigurations, ConfigurationType.TENANT_TEMPLATE_ENGINE, "bonita-tenant-sp-custom.properties");
            addIfExists(tenantTemplateConfigurations, ConfigurationType.TENANT_TEMPLATE_ENGINE, "bonita-tenant-sp-cluster-custom.properties");
            addIfExists(tenantTemplateConfigurations, ConfigurationType.TENANT_TEMPLATE_ENGINE, "bonita-tenant-sp-custom.xml");
            configurationService.storeTenantTemplateEngineConf(tenantTemplateConfigurations);

            List<BonitaConfiguration> securityScripts = new ArrayList<>();
            addIfExists(securityScripts, ConfigurationType.TENANT_TEMPLATE_SECURITY_SCRIPTS, "ActorMemberPermissionRule.groovy");
            addIfExists(securityScripts, ConfigurationType.TENANT_TEMPLATE_SECURITY_SCRIPTS, "ActorPermissionRule.groovy");
            addIfExists(securityScripts, ConfigurationType.TENANT_TEMPLATE_SECURITY_SCRIPTS, "CaseContextPermissionRule.groovy");
            addIfExists(securityScripts, ConfigurationType.TENANT_TEMPLATE_SECURITY_SCRIPTS, "CasePermissionRule.groovy");
            addIfExists(securityScripts, ConfigurationType.TENANT_TEMPLATE_SECURITY_SCRIPTS, "CaseVariablePermissionRule.groovy");
            addIfExists(securityScripts, ConfigurationType.TENANT_TEMPLATE_SECURITY_SCRIPTS, "CommentPermissionRule.groovy");
            addIfExists(securityScripts, ConfigurationType.TENANT_TEMPLATE_SECURITY_SCRIPTS, "ConnectorInstancePermissionRule.groovy");
            addIfExists(securityScripts, ConfigurationType.TENANT_TEMPLATE_SECURITY_SCRIPTS, "DocumentPermissionRule.groovy");
            addIfExists(securityScripts, ConfigurationType.TENANT_TEMPLATE_SECURITY_SCRIPTS, "ProcessConfigurationPermissionRule.groovy");
            addIfExists(securityScripts, ConfigurationType.TENANT_TEMPLATE_SECURITY_SCRIPTS, "ProcessConnectorDependencyPermissionRule.groovy");
            addIfExists(securityScripts, ConfigurationType.TENANT_TEMPLATE_SECURITY_SCRIPTS, "ProcessInstantiationPermissionRule.groovy");
            addIfExists(securityScripts, ConfigurationType.TENANT_TEMPLATE_SECURITY_SCRIPTS, "ProcessPermissionRule.groovy");
            addIfExists(securityScripts, ConfigurationType.TENANT_TEMPLATE_SECURITY_SCRIPTS, "ProcessResolutionProblemPermissionRule.groovy");
            addIfExists(securityScripts, ConfigurationType.TENANT_TEMPLATE_SECURITY_SCRIPTS, "ProcessSupervisorPermissionRule.groovy");
            addIfExists(securityScripts, ConfigurationType.TENANT_TEMPLATE_SECURITY_SCRIPTS, "ProfileEntryPermissionRule.groovy");
            addIfExists(securityScripts, ConfigurationType.TENANT_TEMPLATE_SECURITY_SCRIPTS, "ProfilePermissionRule.groovy");
            addIfExists(securityScripts, ConfigurationType.TENANT_TEMPLATE_SECURITY_SCRIPTS, "TaskExecutionPermissionRule.groovy");
            addIfExists(securityScripts, ConfigurationType.TENANT_TEMPLATE_SECURITY_SCRIPTS, "TaskPermissionRule.groovy");
            addIfExists(securityScripts, ConfigurationType.TENANT_TEMPLATE_SECURITY_SCRIPTS, "UserPermissionRule.groovy");
            configurationService.storeTenantTemplateSecurityScripts(securityScripts);

            List<BonitaConfiguration> portalTenantTemplate = new ArrayList<>();
            addIfExists(portalTenantTemplate, ConfigurationType.TENANT_TEMPLATE_PORTAL, "authenticationManager-config.properties");
            addIfExists(portalTenantTemplate, ConfigurationType.TENANT_TEMPLATE_PORTAL, "compound-permissions-mapping.properties");
            addIfExists(portalTenantTemplate, ConfigurationType.TENANT_TEMPLATE_PORTAL, "console-config.properties");
            addIfExists(portalTenantTemplate, ConfigurationType.TENANT_TEMPLATE_PORTAL, "custom-permissions-mapping.properties");
            addIfExists(portalTenantTemplate, ConfigurationType.TENANT_TEMPLATE_PORTAL, "dynamic-permissions-checks.properties");
            addIfExists(portalTenantTemplate, ConfigurationType.TENANT_TEMPLATE_PORTAL, "forms-config.properties");
            addIfExists(portalTenantTemplate, ConfigurationType.TENANT_TEMPLATE_PORTAL, "resources-permissions-mapping.properties");
            addIfExists(portalTenantTemplate, ConfigurationType.TENANT_TEMPLATE_PORTAL, "security-config.properties");
            configurationService.storeTenantTemplatePortalConf(portalTenantTemplate);

            List<BonitaConfiguration> portalPlatform = new ArrayList<>();
            addIfExists(portalPlatform, ConfigurationType.PLATFORM_PORTAL, "cache-config.xml");
            addIfExists(portalPlatform, ConfigurationType.PLATFORM_PORTAL, "jaas-standard.cfg");
            addIfExists(portalPlatform, ConfigurationType.PLATFORM_PORTAL, "platform-tenant-config.properties");
            addIfExists(portalPlatform, ConfigurationType.PLATFORM_PORTAL, "security-config.properties");
            configurationService.storePlatformPortalConf(portalPlatform);

        } catch (IOException e) {
            throw new PlatformSetupException(e);
        }
    }

    private void addIfExists(List<BonitaConfiguration> tenantTemplateConfigurations, ConfigurationType configurationType, String resourceName)
            throws IOException {
        BonitaConfiguration bonitaConfiguration = getBonitaConfigurationFromClassPath(configurationType.name().toLowerCase(), resourceName);
        if (bonitaConfiguration != null) {
            tenantTemplateConfigurations.add(bonitaConfiguration);
        }
    }

    private void initServices() throws PlatformSetupException {
        if (scriptExecutor == null) {
            scriptExecutor = new ScriptExecutor(dbVendor, dataSource);
        }
        if (configurationService == null) {
            final DataSourceTransactionManager dataSourceTransactionManager = new DataSourceTransactionManager(dataSource);
            configurationService = new ConfigurationServiceImpl(new JdbcTemplate(dataSource), new TransactionTemplate(dataSourceTransactionManager), dbVendor);
        }
        if (versionService == null) {
            versionService = new VersionServiceImpl(new JdbcTemplate(dataSource), dbVendor);
        }
    }

    private void initDataSource() throws PlatformSetupException {
        try {
            if (dataSource == null) {
                dataSource = new DataSourceLookup().lookup();
            }
        } catch (NamingException e) {
            throw new PlatformSetupException(e);
        }
    }

    private BonitaConfiguration getBonitaConfigurationFromClassPath(String folder, String resourceName) throws IOException {
        try (InputStream resourceAsStream = this.getClass().getResourceAsStream("/" + folder + "/" + resourceName)) {
            if (resourceAsStream == null) {
                return null;
            }
            LOGGER.debug("Using configuration from classpath " + resourceName);
            return new BonitaConfiguration(resourceName, IOUtils.toByteArray(resourceAsStream));
        }
    }

    public void destroy() throws PlatformSetupException {
        initPlatformSetup();
        if (isPlatformAlreadyCreated()) {
            scriptExecutor.deleteTables();
        }
    }

    public void initPlatformSetup() throws PlatformSetupException {
        initProperties();
        initDataSource();
        initServices();
    }

    public ConfigurationService getConfigurationService() {
        return configurationService;
    }
}
