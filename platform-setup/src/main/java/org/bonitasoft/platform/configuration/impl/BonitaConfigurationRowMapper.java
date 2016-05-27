/**
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
 **/
package org.bonitasoft.platform.configuration.impl;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.bonitasoft.platform.configuration.model.BonitaConfiguration;
import org.springframework.jdbc.core.RowMapper;

/**
 * @author Laurent Leseigneur
 */
public class BonitaConfigurationRowMapper implements RowMapper<BonitaConfiguration> {

    public static final String SELECT_CONFIGURATION = "SELECT tenant_id, content_type, resource_name, resource_content FROM configuration WHERE tenant_id = ? AND content_type = ? ORDER BY resource_name";

    public static final String TENANT_ID = "tenant_id";
    public static final String CONTENT_TYPE = "content_type";
    public static final String RESOURCE_NAME = "resource_name";
    public static final String RESOURCE_CONTENT = "resource_content";

    @Override
    public BonitaConfiguration mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new BonitaConfiguration(rs.getString(RESOURCE_NAME), rs.getBytes(RESOURCE_CONTENT));
    }
}
