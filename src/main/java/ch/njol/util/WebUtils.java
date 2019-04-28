/*
 *   This file is part of Skript.
 *
 *  Skript is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Skript is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Skript. If not, see <https://www.gnu.org/licenses/>.
 *
 *
 * Copyright 2011-2019 Peter Güttinger and contributors
 *
 */

package ch.njol.util;

import org.eclipse.jdt.annotation.Nullable;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * An utility class about Web.
 *
 * @author TheDGOfficial
 * @since 2.2-Fixes-V10b
 */
public final class WebUtils {

    /**
     * Static magic.
     */
    private WebUtils() {
        throw new UnsupportedOperationException();
    }

    /**
     * Connects to the given address and returns the web response as
     * {@link java.lang.String String}.
     * This the overloaded version of the original
     * {@link WebUtils#getResponse(String, String)} method. This overloaded version
     * of the original method is just uses the default content type (json).
     *
     * @param address - The url (address) of the web server / web site to connect
     *                and get response from it.
     * @return The web response from the given url as {@link java.lang.String
     * String}, maybe null in some cases.
     * @throws IOException If any connection errors occured when making the http
     *                     request to the address.
     */
    @Nullable
    public static final String getResponse(final String address) throws IOException {

        return getResponse(address, "application/json; charset=utf-8");

    }

    /**
     * Connects to the given address and returns the web response as
     * {@link java.lang.String String}.
     *
     * @param address     - The url (address) of the web server / web site to
     *                    connect and get response from it.
     * @param contentType - The content type header of the http web request to the
     *                    selected address / url.
     * @return The web response from the given url as {@link java.lang.String
     * String}, maybe null in some cases.
     * @throws IOException If any connection errors occured when making the http
     *                     request to the address.
     */
    @Nullable
    public static final String getResponse(final String address, final String contentType) throws IOException {

        String response;

        BufferedInputStream in = null;
        BufferedReader br = null;

        try {

            final URL url = new URL(address);
            final URLConnection con = url.openConnection();

            con.setAllowUserInteraction(false);
            con.setDoOutput(false);
            con.setUseCaches(false);
            con.setRequestProperty("Method", "GET");
            con.setRequestProperty("Charset", "UTF-8");
            con.setRequestProperty("Encoding", "UTF-8");
            con.setRequestProperty("Content-Type", contentType);
            con.setRequestProperty("Accept", "*/*");
            con.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/66.0.3359.181 Safari/537.36");
            con.setRequestProperty("Referer", "https://www.lifemcserver.com/forum/");

            in = new BufferedInputStream(con.getInputStream());

            final String encoding = con.getContentEncoding();

            if (encoding != null) {

                if ("gzip".equalsIgnoreCase(encoding)) {

                    in = new BufferedInputStream(new GZIPInputStream(in));

                } else if ("deflate".equalsIgnoreCase(encoding)) {

                    in = new BufferedInputStream(new InflaterInputStream(in, new Inflater(true)));

                }

            }

            final StringBuilder responseBody = new StringBuilder(4096);
            br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));

            String line;

            while ((line = br.readLine()) != null) {

                responseBody.append(line.trim());
                responseBody.append("\n");

            }

            in.close();
            br.close();

            in = null;
            br = null;

            response = responseBody.toString();

            if (response != null) {

                response = response.trim();

            }

            return response;

        } finally {

            if (in != null) {

                in.close();

            }

            if (br != null) {

                br.close();

            }

        }

    }

}
