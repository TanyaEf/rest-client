package org.wiztools.restclient;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.wiztools.commons.CommonCharset;
import org.wiztools.commons.StringUtil;

/**
 *
 * @author schandran
 */
public final class Util {

    // private constructor so that no instance from outside can be created
    private Util() {
    }

    public static String getStackTrace(final Throwable aThrowable) {
        String errorMsg = aThrowable.getMessage();
        final Writer result = new StringWriter();
        final PrintWriter printWriter = new PrintWriter(result);
        aThrowable.printStackTrace(printWriter);
        return errorMsg + "\n" + result.toString();
    }

    public static String getHTMLListFromList(List<String> ll) {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><ul>");
        for (String str : ll) {
            sb.append("<li>").append(str).append("</li>");
        }
        sb.append("</ul></html>");
        return sb.toString();
    }

    public static String parameterEncode(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (String key : params.keySet()) {
            try {
                String value = params.get(key);
                String encodedKey = URLEncoder.encode(key, CommonCharset.UTF_8.name());
                String encodedValue = URLEncoder.encode(value, CommonCharset.UTF_8.name());
                sb.append(encodedKey).append("=").append(encodedValue).append("&");
            } catch (UnsupportedEncodingException ex) {
                assert true : "Encoder UTF-8 supported in all Java platforms.";
            }
        }
        sb.deleteCharAt(sb.length() - 1);
        return sb.toString();
    }

    public static void createReqResArchive(Request request, Response response, File zipFile)
            throws IOException, XMLException {
        File requestFile = File.createTempFile("req-", ".xml");
        File responseFile = File.createTempFile("res-", ".xml");
        XMLUtil.writeRequestXML(request, requestFile);
        XMLUtil.writeResponseXML(response, responseFile);

        Map<String, File> files = new HashMap<String, File>();
        files.put("request.rcq", requestFile);
        files.put("response.rcs", responseFile);
        byte[] buf = new byte[BUFF_SIZE];
        ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile));
        boolean isSuccess = false;
        try {
            for (String entryName : files.keySet()) {
                File entryFile = files.get(entryName);
                FileInputStream fis = new FileInputStream(entryFile);
                zos.putNextEntry(new ZipEntry(entryName));
                int len;
                while ((len = fis.read(buf)) > 0) {
                    zos.write(buf, 0, len);
                }
                zos.closeEntry();
                fis.close();
            }
            isSuccess = true;
        } finally {
            IOException ioe = null;
            if (zos != null) {
                try {
                    zos.close();
                } catch (IOException ex) {
                    isSuccess = false;
                    ioe = ex;
                }
            }
            if (!isSuccess) { // Failed: delete half-written zip file
                zipFile.delete();
            }
            requestFile.delete();
            responseFile.delete();
            if (ioe != null) {
                throw ioe;
            }
        }
    }
    private static final int BUFF_SIZE = 1024 * 4;

    public static ReqResBean getReqResArchive(File zipFile)
            throws FileNotFoundException, IOException, XMLException {
        ReqResBean encpBean = new ReqResBean();
        // BufferedOutputStream dest = null;
        FileInputStream fis = new FileInputStream(zipFile);
        ZipInputStream zis = new ZipInputStream(new BufferedInputStream(fis));
        ZipEntry entry;
        try {
            boolean isReqRead = false;
            boolean isResRead = false;
            while ((entry = zis.getNextEntry()) != null) {
                int count;
                byte data[] = new byte[BUFF_SIZE];
                File tmpFile = File.createTempFile(entry.getName(), "");
                try {
                    FileOutputStream fos = new FileOutputStream(tmpFile);
                    BufferedOutputStream dest = new BufferedOutputStream(fos, BUFF_SIZE);
                    while ((count = zis.read(data, 0, BUFF_SIZE)) != -1) {
                        dest.write(data, 0, count);
                    }
                    dest.flush();
                    dest.close();

                    if (entry.getName().equals("request.rcq")) {
                        Request reqBean = XMLUtil.getRequestFromXMLFile(tmpFile);
                        encpBean.setRequestBean(reqBean);
                        isReqRead = true;
                    } else if (entry.getName().equals("response.rcs")) {
                        Response resBean = XMLUtil.getResponseFromXMLFile(tmpFile);
                        encpBean.setResponseBean(resBean);
                        isResRead = true;
                    }
                } finally {
                    tmpFile.delete();
                }
            }
            if ((!isReqRead) || (!isResRead)) {
                throw new IOException("Archive does not have request.rcq/response.rcs!");
            }
        } finally {
            zis.close();
        }
        return encpBean;
    }

    /**
     * Parses the HTTP response status line, and returns the status code.
     * @param statusLine
     * @return The status code from HTTP response status line.
     */
    public static final int getStatusCodeFromStatusLine(final String statusLine) {
        int retVal = -1;
        final String STATUS_PATTERN = "[^\\s]+\\s([0-9]{3})\\s.*";
        Pattern p = Pattern.compile(STATUS_PATTERN);
        Matcher m = p.matcher(statusLine);
        if (m.matches()) {
            retVal = Integer.parseInt(m.group(1));
        }
        return retVal;
    }

    /**
     * Method formats content-type and charset for use as HTTP header value
     * @param contentType
     * @param charset
     * @return The formatted content-type and charset.
     */
    public static final String getFormattedContentType(final String contentType, final String charset) {
        String charsetFormatted = StringUtil.isStrEmpty(charset) ? "" : "; charset=" + charset;
        return contentType + charsetFormatted;
    }

    public static final String getCharsetFromHeader(final String contentType){
        Pattern p = Pattern.compile(".*charset=([^;]*).*");
        Matcher m = p.matcher(contentType);
        if(m.matches()){
            String charset = m.group(1);
            return charset;
        }
        // if no match:
        return null;
    }

    public static final String getCharsetFromHeader(final Map<String, String> headers){
        for(String key: headers.keySet()){
            if(key.equalsIgnoreCase("Content-type")){
                return getCharsetFromHeader(headers.get(key));
            }
        }
        return null;
    }
}