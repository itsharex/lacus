package com.lacus.common.utils.hdfs;

import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.*;
import org.apache.hadoop.io.IOUtils;
import org.apache.hadoop.io.compress.CompressionCodec;
import org.apache.hadoop.io.compress.CompressionCodecFactory;
import org.apache.hadoop.io.compress.CompressionOutputStream;
import org.apache.hadoop.util.ReflectionUtils;

import java.io.*;
import java.net.URI;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
public class HdfsUtil {

    private static Configuration conf;

    private static final String HADOOP_USER_KEY = "HADOOP_USER_NAME";

    public static final String DEFAULT_HDFS = "hdfs.defaultFS";

    private static void init(String defaultHdfs, String hadoopUserName) {
        envSetting(hadoopUserName);
        if (conf == null) {
            conf = new Configuration();
            conf.set(DEFAULT_HDFS, defaultHdfs);
        }
    }

    /**
     * 设置hadoop用户环境变量
     */
    public static void envSetting(String hadoopUserName) {
        System.setProperty(HADOOP_USER_KEY, Objects.requireNonNull(hadoopUserName));
    }

    /**
     * 查看hdfs文件列表
     */
    public static FileStatus[] listPaths(String defaultHdfs, String hadoopUserName, String filePath) {
        init(defaultHdfs, hadoopUserName);
        FileStatus[] fileStatuses;
        try {
            FileSystem fs = FileSystem.get(conf);
            fileStatuses = fs.listStatus(new Path(filePath));
            fs.close();
            return fileStatuses;
        } catch (FileNotFoundException fex) {
            log.error("找不到HDFS路径:", fex);
            throw new RuntimeException("找不到HDFS路径:" + filePath);
        } catch (Exception ex) {
            log.error("HDFS 连接失败:", ex);
            throw new RuntimeException("HDFS 连接失败");
        }
    }

    /**
     * 新建hdfs文件
     */
    public static void createFile(String filePath, byte[] data) throws IOException {
        FileSystem fs = FileSystem.get(conf);
        FSDataOutputStream outputStream = fs.create(new Path(filePath));
        outputStream.write(data);
        outputStream.close();
        fs.close();
    }

    /**
     * 新建文件
     */
    public static void createFile(String filePath, String data) throws IOException {
        createFile(filePath, data.getBytes());
    }

    /**
     * 从本地上传到HDFS
     *
     * @param localPath  本地文件路径
     * @param remotePath HDF文件S路径
     */
    public static void copyFileFromLocal(String localPath, String remotePath) throws IllegalArgumentException, IOException {
        FileSystem fs = FileSystem.get(conf);
        fs.copyFromLocalFile(new Path(localPath), new Path(remotePath));
    }

    /**
     * 从HDFS下载到本地
     *
     * @param remotePath hdfs路径
     * @param localPath  HDF文件S路径
     */
    public static void copyToLocalFile(String defaultHdfs, String hadoopUserName, String remotePath, String localPath) throws IllegalArgumentException, IOException {
        init(defaultHdfs, hadoopUserName);
        FileSystem fs = FileSystem.get(conf);
        fs.copyToLocalFile(false, new Path(remotePath), new Path(localPath), true);
    }

    /**
     * 递归删除文件
     */
    public static boolean deleteFileRecursive(String defaultHdfs, String hadoopUserName, String filePath) throws IllegalArgumentException, IOException {
        return deleteFile(defaultHdfs, hadoopUserName, filePath, true);
    }

    /**
     * 非递归删除文件
     */
    public static boolean deleteFile(String defaultHdfs, String hadoopUserName, String filePath) throws IllegalArgumentException, IOException {
        return deleteFile(defaultHdfs, hadoopUserName, filePath, false);
    }

    private static boolean deleteFile(String defaultHdfs, String hadoopUserName, String filePath, boolean recursive) throws IllegalArgumentException, IOException {
        init(defaultHdfs, hadoopUserName);
        FileSystem fs = FileSystem.get(conf);
        return fs.delete(new Path(filePath), recursive);
    }

    /**
     * 创建文件夹
     */
    public static boolean mkdir(String defaultHdfs, String hadoopUserName, String dirPath) throws IllegalArgumentException, IOException {
        init(defaultHdfs, hadoopUserName);
        FileSystem fs = FileSystem.get(conf);
        return fs.mkdirs(new Path(dirPath));
    }

    /**
     * 读取文件内容
     */
    public static String readFile(String defaultHdfs, String hadoopUserName, String filePath) throws IOException {
        init(defaultHdfs, hadoopUserName);
        String res;
        FileSystem fs;
        FSDataInputStream inputStream = null;
        ByteArrayOutputStream outputStream = null;
        try {
            fs = FileSystem.get(conf);
            inputStream = fs.open(new Path(filePath));
            outputStream = new ByteArrayOutputStream(inputStream.available());
            IOUtils.copyBytes(inputStream, outputStream, conf);
            res = outputStream.toString();
        } finally {
            if (inputStream != null) {
                IOUtils.closeStream(inputStream);
            }
            if (outputStream != null) {
                IOUtils.closeStream(outputStream);
            }
        }
        return res;
    }

    /**
     * 判断路径在HDFS上是否存在
     *
     * @param path 路径
     */
    public static boolean exists(String defaultHdfs, String hadoopUserName, String path) {
        init(defaultHdfs, hadoopUserName);
        FileSystem fs;
        try {
            fs = FileSystem.get(conf);
            return fs.exists(new Path(path));
        } catch (Exception ex) {
            log.error("HDFS 连接失败:", ex);
            throw new RuntimeException("HDFS 连接失败");
        }
    }

    /**
     * 压缩文件
     *
     * @param codecClassName 压缩类名
     * @param filePath       被压缩的文件路径
     * @param compressPath   压缩文件路径
     */
    public static void compress(String defaultHdfs, String hadoopUserName, String codecClassName, String filePath, String compressPath) throws Exception {
        init(defaultHdfs, hadoopUserName);
        Class<?> codecClass = Class.forName(codecClassName);
        FileSystem fs = FileSystem.get(conf);
        CompressionCodec codec = (CompressionCodec) ReflectionUtils.newInstance(codecClass, conf);
        // 指定压缩文件路径
        FSDataOutputStream outputStream = fs.create(new Path(compressPath));
        // 指定要被压缩的文件路径
        FSDataInputStream in = fs.open(new Path(filePath));
        // 创建压缩输出流
        CompressionOutputStream out = codec.createOutputStream(outputStream);
        IOUtils.copyBytes(in, out, conf);
        IOUtils.closeStream(in);
        IOUtils.closeStream(out);
    }

    /**
     * 压缩文件夹
     *
     * @param baseDir         文件目录
     * @param zipOutputStream zip文件输出流
     */
    public static void compressFolder(String defaultHdfs, String hadoopUserName, String baseDir, ZipOutputStream zipOutputStream) throws IOException {
        try {
            init(defaultHdfs, hadoopUserName);
            FileSystem fs = FileSystem.get(conf);
            FileStatus[] fileStatusList = fs.listStatus(new Path(baseDir));
            log.info("basedir = " + baseDir);

            for (FileStatus fileStatus : fileStatusList) {
                String name = fileStatus.getPath().toString();
                name = new File(name).getName();
                name = name.replace("_0.xlsx", ".xlsx");

                if (fileStatus.isFile()) {
                    Path path = fileStatus.getPath();
                    FSDataInputStream inputStream = fs.open(path);
                    zipOutputStream.putNextEntry(new ZipEntry(name));
                    IOUtils.copyBytes(inputStream, zipOutputStream, Integer.parseInt("1024"));
                    inputStream.close();
                } else {
                    zipOutputStream.putNextEntry(new ZipEntry(fileStatus.getPath().getName() + "/"));
                    log.info("file = " + fileStatus.getPath().toString());
                    compressFolder(defaultHdfs, hadoopUserName, fileStatus.getPath().toString(), zipOutputStream);
                }
            }
        } catch (IOException e) {
            log.info("----error:{}----", e.getMessage());
        }
    }

    /**
     * 解压缩
     *
     * @param fileName       文件名称
     * @param codecClassName 压缩类型
     */
    public static void uncompress(String fileName, String codecClassName) throws Exception {
        Class<?> codecClass = Class.forName(codecClassName);
        Configuration conf = new Configuration();
        FileSystem fs = FileSystem.get(conf);
        CompressionCodec codec = (CompressionCodec) ReflectionUtils.newInstance(codecClass, conf);
        FSDataInputStream inputStream = fs.open(new Path(fileName));
        // 把text文件里到数据解压，然后输出到控制台
        InputStream in = codec.createInputStream(inputStream);
        IOUtils.copyBytes(in, System.out, conf);
        IOUtils.closeStream(in);
    }

    /**
     * 使用文件扩展名来推断codec来对文件进行解压缩
     *
     * @param uri 文件路径
     */
    public static void uncompressByExtension(String uri) throws IOException {
        Configuration conf = new Configuration();
        FileSystem fs = FileSystem.get(URI.create(uri), conf);

        Path inputPath = new Path(uri);
        CompressionCodecFactory factory = new CompressionCodecFactory(conf);
        CompressionCodec codec = factory.getCodec(inputPath);
        if (codec == null) {
            System.out.println("no codec found for " + uri);
            System.exit(1);
        }
        String outputUri = CompressionCodecFactory.removeSuffix(uri, codec.getDefaultExtension());
        InputStream in = null;
        OutputStream out = null;
        try {
            in = codec.createInputStream(fs.open(inputPath));
            out = fs.create(new Path(outputUri));
            IOUtils.copyBytes(in, out, conf);
        } finally {
            IOUtils.closeStream(out);
            IOUtils.closeStream(in);
        }
    }

    public static void copyFileFromHdfs(String defaultHdfs, String hadoopUserName, String fsFile, String LocalDir) throws IOException {
        init(defaultHdfs, hadoopUserName);
        FileSystem fs = FileSystem.get(conf); // fs是HDFS文件系统

        Path HDFSFile = new Path(fsFile);
        FileStatus[] status = fs.listStatus(HDFSFile); // 得到输入目录
        FileOutputStream outFile = new FileOutputStream(LocalDir);

        for (FileStatus st : status) {
            Path temp = st.getPath();

            FSDataInputStream in = fs.open(temp);
            FileOutputStream out = new FileOutputStream(LocalDir);
            IOUtils.copyBytes(in, out, 4096, false); // 读取in流中的内容放入out

            out.close();
            in.close();
        }
        outFile.close();
    }
}