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

package org.bonitasoft.platform.setup;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.OS;

/**
 * @author Baptiste Mesta
 */
public class PlatformSetupTestUtils {

    private static void writeZipInputToFile(final ZipInputStream zipInputstream, final File outputFile) throws FileNotFoundException, IOException {
        // The input is a file. An FileOutputStream is created to write the content of the new file.
        outputFile.getParentFile().mkdirs();
        try (FileOutputStream fileOutputStream = new FileOutputStream(outputFile)) {
            // The contents of the new file, that is read from the ZipInputStream using a buffer (byte []), is written.
            int bytesRead;
            final byte[] buffer = new byte[1024];
            while ((bytesRead = zipInputstream.read(buffer)) > -1) {
                fileOutputStream.write(buffer, 0, bytesRead);
            }
            fileOutputStream.flush();
        } catch (final IOException ioe) {
            // In case of error, the file is deleted
            outputFile.delete();
            throw ioe;
        }
    }

    private static void extractZipEntries(final ZipInputStream zipInputstream, final File outputFolder) throws IOException {
        ZipEntry zipEntry;
        while ((zipEntry = zipInputstream.getNextEntry()) != null) {
            try {
                // For each entry, a file is created in the output directory "folder"
                final File outputFile = new File(outputFolder.getAbsolutePath(), zipEntry.getName());
                // If the entry is a directory, it creates in the output folder, and we go to the next entry (continue).
                if (zipEntry.isDirectory()) {
                    outputFile.mkdirs();
                    continue;
                }
                writeZipInputToFile(zipInputstream, outputFile);
            } finally {
                zipInputstream.closeEntry();
            }
        }
    }

    private static void unzipToFolder(final InputStream inputStream, final File outputFolder) throws IOException {
        try (ZipInputStream zipInputstream = new ZipInputStream(inputStream)) {
            extractZipEntries(zipInputstream, outputFolder);
        }
    }

    public static void extractDistributionTo(File distFolder) throws IOException {
        File target = new File("target");
        Pattern distribPattern = Pattern.compile("Bonita-BPM-platform-setup-.*\\.zip");
        File dist = null;
        for (File file : target.listFiles()) {
            if (distribPattern.matcher(file.getName()).matches()) {
                dist = file;
                break;
            }
        }
        if (dist == null) {
            throw new IllegalStateException("Unable to locate the distribution");
        }

        try (InputStream inputStream = new FileInputStream(dist)) {
            unzipToFolder(inputStream, distFolder);
        }
    }

    public static Connection getJdbcConnection(File distFolder) throws SQLException {
        Connection conn = DriverManager.getConnection(
                "jdbc:h2:" + distFolder.getAbsolutePath() + "/../database/bonita_journal.db",
                "sa", "");
        return conn;
    }

    public static DefaultExecutor createExecutor(File distFolder) {
        DefaultExecutor oDefaultExecutor = new DefaultExecutor();
        oDefaultExecutor.setWorkingDirectory(distFolder);
        return oDefaultExecutor;
    }

    public static CommandLine createCommandLine() {
        if (OS.isFamilyWindows() || OS.isFamilyWin9x()) {
            return new CommandLine("setup.bat");
        } else {
            CommandLine oCmdLine = new CommandLine("sh");
            oCmdLine.addArgument("setup.sh");
            return oCmdLine;
        }
    }
}
