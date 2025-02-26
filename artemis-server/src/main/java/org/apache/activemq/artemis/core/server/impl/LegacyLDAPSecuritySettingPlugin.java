/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.artemis.core.server.impl;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.event.EventDirContext;
import javax.naming.event.NamespaceChangeListener;
import javax.naming.event.NamingEvent;
import javax.naming.event.NamingExceptionEvent;
import javax.naming.event.ObjectChangeListener;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.ActiveMQServerLogger;
import org.apache.activemq.artemis.core.server.SecuritySettingPlugin;
import org.apache.activemq.artemis.core.settings.HierarchicalRepository;
import org.jboss.logging.Logger;

public class LegacyLDAPSecuritySettingPlugin implements SecuritySettingPlugin {

   private static final Logger logger = Logger.getLogger(LegacyLDAPSecuritySettingPlugin.class);

   private static final long serialVersionUID = 4793109879399750045L;

   public static final String INITIAL_CONTEXT_FACTORY = "initialContextFactory";
   public static final String CONNECTION_URL = "connectionURL";
   public static final String CONNECTION_USERNAME = "connectionUsername";
   public static final String CONNECTION_PASSWORD = "connectionPassword";
   public static final String CONNECTION_PROTOCOL = "connectionProtocol";
   public static final String AUTHENTICATION = "authentication";
   public static final String ROLE_ATTRIBUTE = "roleAttribute";
   public static final String FILTER = "filter";
   public static final String DESTINATION_BASE = "destinationBase";
   public static final String ADMIN_PERMISSION_VALUE = "adminPermissionValue";
   public static final String READ_PERMISSION_VALUE = "readPermissionValue";
   public static final String WRITE_PERMISSION_VALUE = "writePermissionValue";
   public static final String ENABLE_LISTENER = "enableListener";
   public static final String MAP_ADMIN_TO_MANAGE = "mapAdminToManage";
   public static final String ALLOW_QUEUE_ADMIN_ON_READ = "allowQueueAdminOnRead";

   private String initialContextFactory = "com.sun.jndi.ldap.LdapCtxFactory";
   private String connectionURL = "ldap://localhost:1024";
   private String connectionUsername;
   private String connectionPassword;
   private String connectionProtocol;
   private String authentication = "simple";
   private String destinationBase = "ou=destinations,o=ActiveMQ,ou=system";
   private String filter = "(cn=*)";
   private String roleAttribute = "uniqueMember";
   private String adminPermissionValue = "admin";
   private String readPermissionValue = "read";
   private String writePermissionValue = "write";
   private boolean enableListener = true;
   private boolean mapAdminToManage = false;
   private boolean allowQueueAdminOnRead = false;

   private DirContext context;
   private EventDirContext eventContext;
   private Map<String, Set<Role>> securityRoles;
   private HierarchicalRepository<Set<Role>> securityRepository;

   @Override
   public LegacyLDAPSecuritySettingPlugin init(Map<String, String> options) {
      if (options != null) {
         initialContextFactory = getOption(options, INITIAL_CONTEXT_FACTORY, initialContextFactory);
         connectionURL = getOption(options, CONNECTION_URL, connectionURL);
         connectionUsername = getOption(options, CONNECTION_USERNAME, connectionUsername);
         connectionPassword = getOption(options, CONNECTION_PASSWORD, connectionPassword);
         connectionProtocol = getOption(options, CONNECTION_PROTOCOL, connectionProtocol);
         authentication = getOption(options, AUTHENTICATION, authentication);
         destinationBase = getOption(options, DESTINATION_BASE, destinationBase);
         filter = getOption(options, FILTER, filter);
         roleAttribute = getOption(options, ROLE_ATTRIBUTE, roleAttribute);
         adminPermissionValue = getOption(options, ADMIN_PERMISSION_VALUE, adminPermissionValue);
         readPermissionValue = getOption(options, READ_PERMISSION_VALUE, readPermissionValue);
         writePermissionValue = getOption(options, WRITE_PERMISSION_VALUE, writePermissionValue);
         enableListener = getOption(options, ENABLE_LISTENER, Boolean.TRUE.toString()).equalsIgnoreCase(Boolean.TRUE.toString());
         mapAdminToManage = getOption(options, MAP_ADMIN_TO_MANAGE, Boolean.FALSE.toString()).equalsIgnoreCase(Boolean.TRUE.toString());
         allowQueueAdminOnRead = getOption(options, ALLOW_QUEUE_ADMIN_ON_READ, Boolean.FALSE.toString()).equalsIgnoreCase(Boolean.TRUE.toString());
      }

      return this;
   }

   private String getOption(Map<String, String> options, String key, String defaultValue) {
      String result = options.get(key);
      if (result == null) {
         result = defaultValue;
      }

      return result;
   }

   public String getRoleAttribute() {
      return roleAttribute;
   }

   public SecuritySettingPlugin setRoleAttribute(String roleAttribute) {
      this.roleAttribute = roleAttribute;
      return this;
   }

   public String getFilter() {
      return filter;
   }

   public LegacyLDAPSecuritySettingPlugin setFilter(String filter) {
      this.filter = filter;
      return this;
   }

   public String getDestinationBase() {
      return destinationBase;
   }

   public LegacyLDAPSecuritySettingPlugin setDestinationBase(String destinationBase) {
      this.destinationBase = destinationBase;
      return this;
   }

   public String getAuthentication() {
      return authentication;
   }

   public LegacyLDAPSecuritySettingPlugin setAuthentication(String authentication) {
      this.authentication = authentication;
      return this;
   }

   public String getConnectionPassword() {
      return connectionPassword;
   }

   public LegacyLDAPSecuritySettingPlugin setConnectionPassword(String connectionPassword) {
      this.connectionPassword = connectionPassword;
      return this;
   }

   public String getConnectionProtocol() {
      return connectionProtocol;
   }

   public LegacyLDAPSecuritySettingPlugin setConnectionProtocol(String connectionProtocol) {
      this.connectionProtocol = connectionProtocol;
      return this;
   }

   public String getConnectionURL() {
      return connectionURL;
   }

   public LegacyLDAPSecuritySettingPlugin setConnectionURL(String connectionURL) {
      this.connectionURL = connectionURL;
      return this;
   }

   public String getConnectionUsername() {
      return connectionUsername;
   }

   public LegacyLDAPSecuritySettingPlugin setConnectionUsername(String connectionUsername) {
      this.connectionUsername = connectionUsername;
      return this;
   }

   public String getInitialContextFactory() {
      return initialContextFactory;
   }

   public String getAdminPermissionValue() {
      return adminPermissionValue;
   }

   public LegacyLDAPSecuritySettingPlugin setAdminPermissionValue(String adminPermissionValue) {
      this.adminPermissionValue = adminPermissionValue;
      return this;
   }

   public String getReadPermissionValue() {
      return readPermissionValue;
   }

   public LegacyLDAPSecuritySettingPlugin setReadPermissionValue(String readPermissionValue) {
      this.readPermissionValue = readPermissionValue;
      return this;
   }

   public String getWritePermissionValue() {
      return writePermissionValue;
   }

   public LegacyLDAPSecuritySettingPlugin setWritePermissionValue(String writePermissionValue) {
      this.writePermissionValue = writePermissionValue;
      return this;
   }

   public LegacyLDAPSecuritySettingPlugin setInitialContextFactory(String initialContextFactory) {
      this.initialContextFactory = initialContextFactory;
      return this;
   }

   public boolean isEnableListener() {
      return enableListener;
   }

   public LegacyLDAPSecuritySettingPlugin setEnableListener(boolean enableListener) {
      this.enableListener = enableListener;
      return this;
   }

   public boolean isMapAdminToManage() {
      return mapAdminToManage;
   }

   public LegacyLDAPSecuritySettingPlugin setMapAdminToManage(boolean mapAdminToManage) {
      this.mapAdminToManage = mapAdminToManage;
      return this;
   }

   public boolean isAllowQueueAdminOnRead() {
      return allowQueueAdminOnRead;
   }

   public LegacyLDAPSecuritySettingPlugin setAllowQueueAdminOnRead(boolean allowQueueAdminOnRead) {
      this.allowQueueAdminOnRead = allowQueueAdminOnRead;
      return this;
   }

   protected boolean isContextAlive() {
      boolean alive = false;
      if (context != null) {
         try {
            context.getAttributes("");
            alive = true;
         } catch (Exception e) {
         }
      }
      return alive;
   }

   protected void open() throws NamingException {
      if (isContextAlive()) {
         return;
      }

      context = createContext();
      eventContext = ((EventDirContext) context.lookup(""));

      SearchControls searchControls = new SearchControls();
      searchControls.setReturningAttributes(new String[]{roleAttribute});
      searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);

      if (enableListener) {
         eventContext.addNamingListener(destinationBase, filter, searchControls, new LDAPNamespaceChangeListener());
      }
   }

   private DirContext createContext() throws NamingException {
      Hashtable<String, String> env = new Hashtable<>();
      env.put(Context.INITIAL_CONTEXT_FACTORY, initialContextFactory);
      if (connectionUsername != null && !"".equals(connectionUsername)) {
         env.put(Context.SECURITY_PRINCIPAL, connectionUsername);
      } else {
         throw new NamingException("Empty username is not allowed");
      }
      if (connectionPassword != null && !"".equals(connectionPassword)) {
         env.put(Context.SECURITY_CREDENTIALS, connectionPassword);
      } else {
         throw new NamingException("Empty password is not allowed");
      }
      env.put(Context.SECURITY_PROTOCOL, connectionProtocol);
      env.put(Context.PROVIDER_URL, connectionURL);
      env.put(Context.SECURITY_AUTHENTICATION, authentication);
      return new InitialDirContext(env);
   }

   @Override
   public Map<String, Set<Role>> getSecurityRoles() {
      if (securityRoles == null) {
         populateSecurityRoles();
      }
      return securityRoles;
   }

   private LegacyLDAPSecuritySettingPlugin populateSecurityRoles() {
      ActiveMQServerLogger.LOGGER.populatingSecurityRolesFromLDAP(connectionURL);
      try {
         open();
      } catch (Exception e) {
         ActiveMQServerLogger.LOGGER.errorOpeningContextForLDAP(e);
         return this;
      }

      SearchControls searchControls = new SearchControls();
      searchControls.setReturningAttributes(new String[]{roleAttribute});
      searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);

      securityRoles = new HashMap<>();
      try {
         if (logger.isDebugEnabled()) {
            logger.debug(new StringBuilder().append("Performing LDAP search: ").append(destinationBase)
                                            .append("\n\tfilter: ").append(filter)
                                            .append("\n\tcontrols:")
                                            .append("\n\t\treturningAttributes: ").append(roleAttribute)
                                            .append("\n\t\tsearchScope: SUBTREE_SCOPE"));
         }
         NamingEnumeration<SearchResult> searchResults = context.search(destinationBase, filter, searchControls);
         while (searchResults.hasMore()) {
            processSearchResult(securityRoles, searchResults.next());
         }
      } catch (Exception e) {
         ActiveMQServerLogger.LOGGER.errorPopulatingSecurityRolesFromLDAP(e);
      }

      return this;
   }

   @Override
   public void setSecurityRepository(HierarchicalRepository<Set<Role>> securityRepository) {
      this.securityRepository = securityRepository;
   }

   private void processSearchResult(Map<String, Set<Role>> securityRoles,
                                    SearchResult searchResult) throws NamingException {
      LdapName searchResultLdapName = new LdapName(searchResult.getName());
      Attributes attrs = searchResult.getAttributes();
      if (attrs == null || attrs.size() == 0) {
         if (logger.isDebugEnabled()) {
            logger.debug("Skipping LDAP search result \"" + searchResultLdapName + "\" with " + (attrs == null ? "null" : attrs.size()) + " attributes");
         }
         return;
      }
      List<Rdn> rdns = searchResultLdapName.getRdns();
      if (rdns.size() < 3) {
         if (logger.isDebugEnabled()) {
            logger.debug("\tSkipping LDAP search result \"" + searchResultLdapName + "\" with " + rdns.size() + " RDNs.");
         }
         return;
      }
      StringBuilder logMessage = new StringBuilder();
      if (logger.isDebugEnabled()) {
         logMessage.append("LDAP search result: ").append(searchResultLdapName);
      }
      // we can count on the RDNs being in order from right to left
      Rdn rdn = rdns.get(rdns.size() - 3);
      String rawDestinationType = rdn.getValue().toString();
      String destinationType = "unknown";
      if (rawDestinationType.toLowerCase().contains("queue")) {
         destinationType = "queue";
      } else if (rawDestinationType.toLowerCase().contains("topic")) {
         destinationType = "topic";
      }
      if (logger.isDebugEnabled()) {
         logMessage.append("\n\tDestination type: ").append(destinationType);
      }

      rdn = rdns.get(rdns.size() - 2);
      if (logger.isDebugEnabled()) {
         logMessage.append("\n\tDestination name: ").append(rdn.getValue());
      }
      String destination = rdn.getValue().toString();

      rdn = rdns.get(rdns.size() - 1);
      if (logger.isDebugEnabled()) {
         logMessage.append("\n\tPermission type: ").append(rdn.getValue());
      }
      String permissionType = rdn.getValue().toString();

      if (logger.isDebugEnabled()) {
         logMessage.append("\n\tAttributes: ").append(attrs);
      }
      Attribute attr = attrs.get(roleAttribute);
      NamingEnumeration<?> e = attr.getAll();
      Set<Role> roles = securityRoles.get(destination);
      boolean exists = false;
      if (roles == null) {
         roles = new HashSet<>();
      } else {
         exists = true;
      }

      while (e.hasMore()) {
         String value = (String) e.next();
         LdapName ldapname = new LdapName(value);
         rdn = ldapname.getRdn(ldapname.size() - 1);
         String roleName = rdn.getValue().toString();
         if (logger.isDebugEnabled()) {
            logMessage.append("\n\tRole name: ").append(roleName);
         }
         boolean write = permissionType.equalsIgnoreCase(writePermissionValue);
         boolean read = permissionType.equalsIgnoreCase(readPermissionValue);
         boolean admin = permissionType.equalsIgnoreCase(adminPermissionValue);
         Role role = new Role(roleName,
                              write,                                     // send
                              read,                                      // consume
                              (allowQueueAdminOnRead && read) || admin,  // createDurableQueue
                              (allowQueueAdminOnRead && read) || admin,  // deleteDurableQueue
                              (allowQueueAdminOnRead && read) || admin,  // createNonDurableQueue
                              admin,                                     // deleteNonDurableQueue
                              mapAdminToManage ? admin : false,          // manage - map to admin based on configuration
                              read,                                      // browse
                              admin,                                     // createAddress
                              admin);                                    // deleteAddress
         roles.add(role);
      }

      if (logger.isDebugEnabled()) {
         logger.debug(logMessage);
      }

      if (!exists) {
         securityRoles.put(destination, roles);
      }
   }

   @Override
   public SecuritySettingPlugin stop() {
      try {
         eventContext.close();
      } catch (NamingException e) {
         // ignore
      }

      try {
         if (context != null) {
            context.close();
         }
      } catch (NamingException e) {
         // ignore
      }

      return this;
   }

   /**
    * Handler for new policy entries in the directory.
    *
    * @param namingEvent the new entry event that occurred
    */
   public void objectAdded(NamingEvent namingEvent) {
      if (logger.isDebugEnabled()) {
         logger.debug("objectAdded:\n\told binding: " + namingEvent.getOldBinding() + "\n\tnew binding: " + namingEvent.getNewBinding());
      }
      Map<String, Set<Role>> newRoles = new HashMap<>();

      try {
         processSearchResult(newRoles, (SearchResult) namingEvent.getNewBinding());
         for (Map.Entry<String, Set<Role>> entry : newRoles.entrySet()) {
            logger.debug("adding match " + entry.getKey() + ": " + entry.getValue());
            securityRepository.addMatch(entry.getKey(), entry.getValue());
         }
      } catch (NamingException e) {
         ActiveMQServerLogger.LOGGER.failedToProcessEvent(e);
      }
   }

   /**
    * Handler for removed policy entries in the directory.
    *
    * @param namingEvent the removed entry event that occurred
    */
   public void objectRemoved(NamingEvent namingEvent) {
      if (logger.isDebugEnabled()) {
         logger.debug("objectRemoved:\n\told binding: " + namingEvent.getOldBinding() + "\n\tnew binding: " + namingEvent.getNewBinding());
      }

      try {
         LdapName ldapName = new LdapName(namingEvent.getOldBinding().getName());
         List<Rdn> rdns = ldapName.getRdns();
         if (rdns.size() < 3) {
            if (logger.isDebugEnabled()) {
               logger.debug("Skipping old binding name \"" + namingEvent.getOldBinding().getName() + "\" with " + rdns.size() + " RDNs.");
            }
            return;
         }

         String match = rdns.get(rdns.size() - 2).getValue().toString();
         if (logger.isDebugEnabled()) {
            logger.debug("Destination name: " + match);
         }

         if (match != null) {
            Set<Role> roles = securityRepository.getMatch(match);

            List<Role> rolesToRemove = new ArrayList<>();

            for (Rdn rdn : ldapName.getRdns()) {
               if (rdn.getValue().equals(writePermissionValue)) {
                  logger.debug("Removing write permission from " + match);
                  for (Role role : roles) {
                     if (role.isSend()) {
                        rolesToRemove.add(role);
                     }
                  }
               } else if (rdn.getValue().equals(readPermissionValue)) {
                  logger.debug("Removing read permission from " + match);
                  for (Role role : roles) {
                     if (role.isConsume()) {
                        rolesToRemove.add(role);
                     }
                  }
               } else if (rdn.getValue().equals(adminPermissionValue)) {
                  logger.debug("Removing admin permission from " + match);
                  for (Role role : roles) {
                     if (role.isCreateDurableQueue() || role.isCreateNonDurableQueue() || role.isDeleteDurableQueue() || role.isDeleteNonDurableQueue()) {
                        rolesToRemove.add(role);
                     }
                  }
               }

               for (Role roleToRemove : rolesToRemove) {
                  roles.remove(roleToRemove);
               }
            }
         }
      } catch (NamingException e) {
         ActiveMQServerLogger.LOGGER.failedToProcessEvent(e);
      }
   }

   /**
    * @param namingEvent the renaming entry event that occurred
    */
   public void objectRenamed(NamingEvent namingEvent) {

   }

   /**
    * Handler for changed policy entries in the directory.
    *
    * @param namingEvent the changed entry event that occurred
    */
   public void objectChanged(NamingEvent namingEvent) {
      if (logger.isDebugEnabled()) {
         logger.debug("objectChanged:\n\told binding: " + namingEvent.getOldBinding() + "\n\tnew binding: " + namingEvent.getNewBinding());
      }
      objectRemoved(namingEvent);
      objectAdded(namingEvent);
   }

   /**
    * Handler for exception events from the registry.
    *
    * @param namingExceptionEvent the exception event
    */
   public void namingExceptionThrown(NamingExceptionEvent namingExceptionEvent) {
      context = null;
      ActiveMQServerLogger.LOGGER.caughtUnexpectedException(namingExceptionEvent.getException());
   }

   protected class LDAPNamespaceChangeListener implements NamespaceChangeListener, ObjectChangeListener {

      @Override
      public void namingExceptionThrown(NamingExceptionEvent evt) {
         LegacyLDAPSecuritySettingPlugin.this.namingExceptionThrown(evt);
      }

      @Override
      public void objectAdded(NamingEvent evt) {
         LegacyLDAPSecuritySettingPlugin.this.objectAdded(evt);
      }

      @Override
      public void objectRemoved(NamingEvent evt) {
         LegacyLDAPSecuritySettingPlugin.this.objectRemoved(evt);
      }

      @Override
      public void objectRenamed(NamingEvent evt) {
         LegacyLDAPSecuritySettingPlugin.this.objectRenamed(evt);
      }

      @Override
      public void objectChanged(NamingEvent evt) {
         LegacyLDAPSecuritySettingPlugin.this.objectChanged(evt);
      }
   }
}
