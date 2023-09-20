/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.integration;

import org.elasticsearch.client.Request;
import org.elasticsearch.test.SecuritySettingsSourceField;
import org.elasticsearch.xpack.core.security.authc.support.Hasher;
import org.junit.Before;

import java.io.IOException;

public class CreateDocsIndexPrivilegeTests extends AbstractPrivilegeTestCase {
    private static final String INDEX_NAME = "index-1";
    private static final String CREATE_DOC_USER = "create_doc_user";
    private String jsonDoc = """
        { "name" : "elasticsearch", "body": "foo bar" }""";
    private static final String ROLES = """
        all_indices_role:
          indices:
            - names: '*'
              privileges: [ all ]
        create_doc_role:
          indices:
            - names: '*'
              privileges: [ create_doc ]
        """;

    private static final String USERS_ROLES = "all_indices_role:admin\n" + "create_doc_role:" + CREATE_DOC_USER + "\n";

    @Override
    protected boolean addMockHttpTransport() {
        return false; // enable http
    }

    @Override
    protected String configRoles() {
        return super.configRoles() + "\n" + ROLES;
    }

    @Override
    protected String configUsers() {
        final Hasher passwdHasher = getFastStoredHashAlgoForTests();
        final String usersPasswdHashed = new String(passwdHasher.hash(SecuritySettingsSourceField.TEST_PASSWORD_SECURE_STRING));

        return super.configUsers() + "admin:" + usersPasswdHashed + "\n" + CREATE_DOC_USER + ":" + usersPasswdHashed + "\n";
    }

    @Override
    protected String configUsersRoles() {
        return super.configUsersRoles() + USERS_ROLES;
    }

    @Before
    public void insertBaseDocumentsAsAdmin() throws Exception {
        Request request = new Request("PUT", "/" + INDEX_NAME + "/_doc/1");
        request.setJsonEntity(jsonDoc);
        request.addParameter("refresh", "true");
        assertAccessIsAllowed("admin", request);
    }

    public void testCreateDocUserCanIndexNewDocumentsWithAutoGeneratedId() throws IOException {
        assertAccessIsAllowed(CREATE_DOC_USER, "POST", "/" + INDEX_NAME + "/_doc", """
            { "foo" : "bar" }""");
    }

    public void testCreateDocUserCanIndexNewDocumentsWithExternalIdAndOpTypeIsCreate() throws IOException {
        assertAccessIsAllowed(CREATE_DOC_USER, randomFrom("PUT", "POST"), "/" + INDEX_NAME + "/_doc/2?op_type=create", """
            { "foo" : "bar" }""");
    }

    public void testCreateDocUserIsDeniedToIndexNewDocumentsWithExternalIdAndOpTypeIsIndex() throws IOException {
        assertAccessIsDenied(CREATE_DOC_USER, randomFrom("PUT", "POST"), "/" + INDEX_NAME + "/_doc/3", """
            { "foo" : "bar" }""");
    }

    public void testCreateDocUserIsDeniedToIndexUpdatesToExistingDocument() throws IOException {
        assertAccessIsDenied(CREATE_DOC_USER, "POST", "/" + INDEX_NAME + "/_update/1", """
            { "doc" : { "foo" : "baz" } }
            """);
        assertAccessIsDenied(CREATE_DOC_USER, "PUT", "/" + INDEX_NAME + "/_doc/1", """
            { "foo" : "baz" }
            """);
    }

    public void testCreateDocUserCanIndexNewDocumentsWithAutoGeneratedIdUsingBulkApi() throws IOException {
        assertAccessIsAllowed(CREATE_DOC_USER, randomFrom("PUT", "POST"), "/" + INDEX_NAME + "/_bulk", """
            { "index" : { } }
            { "foo" : "bar" }
            """);
    }

    public void testCreateDocUserCanIndexNewDocumentsWithAutoGeneratedIdAndOpTypeCreateUsingBulkApi() throws IOException {
        assertAccessIsAllowed(CREATE_DOC_USER, randomFrom("PUT", "POST"), "/" + INDEX_NAME + "/_bulk", """
            { "create" : { } }
            { "foo" : "bar" }
            """);
    }

    public void testCreateDocUserCanIndexNewDocumentsWithExternalIdAndOpTypeIsCreateUsingBulkApi() throws IOException {
        assertAccessIsAllowed(CREATE_DOC_USER, randomFrom("PUT", "POST"), "/" + INDEX_NAME + "/_bulk", """
            { "create" : { "_id" : "4" } }
            { "foo" : "bar" }
            """);
    }

    public void testCreateDocUserIsDeniedToIndexNewDocumentsWithExternalIdAndOpTypeIsIndexUsingBulkApi() throws IOException {
        assertBodyHasAccessIsDenied(CREATE_DOC_USER, randomFrom("PUT", "POST"), "/" + INDEX_NAME + "/_bulk", """
            { "index" : { "_id" : "5" } }
            { "foo" : "bar" }
            """);
    }

    public void testCreateDocUserIsDeniedToIndexUpdatesToExistingDocumentUsingBulkApi() throws IOException {
        assertBodyHasAccessIsDenied(CREATE_DOC_USER, randomFrom("PUT", "POST"), "/" + INDEX_NAME + "/_bulk", """
            { "index" : { "_id" : "1" } }
            { "doc" : {"foo" : "bazbaz"} }
            """);
        assertBodyHasAccessIsDenied(CREATE_DOC_USER, randomFrom("PUT", "POST"), "/" + INDEX_NAME + "/_bulk", """
            { "update" : { "_id" : "1" } }
            { "doc" : {"foo" : "bazbaz"} }
            """);
    }

}
