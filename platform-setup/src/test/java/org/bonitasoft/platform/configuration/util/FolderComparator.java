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
package org.bonitasoft.platform.configuration.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Laurent Leseigneur
 */
public class FolderComparator {

    public void compare(File configFolder, File destFolder) throws Exception {
        final Map<String, File> expectedFiles = flattenFolderFiles(configFolder);
        final Map<String, File> files = flattenFolderFiles(destFolder);
        assertThat(files).as("should have same size").hasSize(expectedFiles.size());
        assertThat(expectedFiles.keySet()).as("should have same file names").isEqualTo(files.keySet());
        for (String name : expectedFiles.keySet()) {
            compareFileContent(expectedFiles.get(name), files.get(name));
        }

    }

    public Map<String, File> flattenFolderFiles(File folder) throws IOException {
        Map<String, File> fileMap = new HashMap<>();
        final FlattenFolderVisitor flattenFolderVisitor = new FlattenFolderVisitor(fileMap);
        Files.walkFileTree(folder.toPath(), flattenFolderVisitor);
        return fileMap;
    }

    private void compareFileContent(File expectedFile, File file) throws Exception {
        assertThat(expectedFile).as("should have same content for file " + file.getName())
                .usingCharset("UTF-8")
                .hasContentEqualTo(file);
    }
}
