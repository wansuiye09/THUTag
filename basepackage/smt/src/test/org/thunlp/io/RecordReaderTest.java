package org.thunlp.io;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import junit.framework.Assert;
import junit.framework.TestCase;

import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.JobConf;
import org.thunlp.hadoop.FolderWriter;

public class RecordReaderTest extends TestCase {
	private String[] keys = { "aaa", "bbb", "ccc" };
	private String[] values = { "asdfawaaa", "bbawverwab", "awefaweccc" };
	private String filename;

	@Override
	public void setUp() throws IOException {
		File tmpFile = File.createTempFile("testcase", ".txt");
		filename = tmpFile.getAbsolutePath();
	}

	@Override
	public void tearDown() throws IOException {
		File plainText = new File(filename);
		plainText.delete();
		File gzipped = new File(filename + ".gz");
		gzipped.delete();
		File zipped = new File(filename + ".zip");
		zipped.delete();
		Path p = new Path(filename + ".sf");
		FileSystem.get(new JobConf()).delete(p, true);
	}

	public void testReadPlainText() throws IOException {
		FileWriter writer = new FileWriter(new File(filename));
		for (int i = 0; i < values.length; i++) {
			writer.write(values[i] + "\n");
		}
		writer.close();

		RecordReader reader = new RecordReader(filename, "UTF-8", RecordWriter.TYPE_PLAIN_TEXT, false);
		for (int i = 0; i < values.length; i++) {
			Assert.assertTrue(reader.next());
			Assert.assertEquals(values[i], reader.value());
			Assert.assertEquals(i + 1, reader.numRead());
		}
		Assert.assertTrue(!reader.next());
		reader.close();

	}

	public void testReadGzippedText() throws IOException {
		OutputStreamWriter writer = new OutputStreamWriter(
				new GZIPOutputStream(new FileOutputStream(new File(filename + ".gz"))));
		for (int i = 0; i < values.length; i++) {
			writer.write(values[i] + "\n");
		}
		writer.close();

		RecordReader reader = new RecordReader(filename + ".gz", "UTF-8", RecordWriter.TYPE_GZIPPED_TEXT, false);
		for (int i = 0; i < values.length; i++) {
			Assert.assertTrue(reader.next());
			Assert.assertEquals(values[i], reader.value());
			Assert.assertEquals(i + 1, reader.numRead());
		}
		Assert.assertTrue(!reader.next());
		reader.close();
	}

	public void testReadZippedText() throws IOException {
		ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(new File(filename + ".zip")));
		zos.putNextEntry(new ZipEntry("part-00000"));
		OutputStreamWriter writer = new OutputStreamWriter(zos);
		for (int i = 0; i < values.length; i++) {
			writer.write(values[i] + "\n");
		}
		writer.flush();
		// Write a new part. We support multiple text files in a single ZIP
		// file.
		zos.putNextEntry(new ZipEntry("part-00001"));
		for (int i = 0; i < values.length; i++) {
			writer.write(values[i] + "\n");
		}
		writer.close();

		RecordReader reader = new RecordReader(filename + ".zip", "UTF-8", RecordWriter.TYPE_ZIPPED_TEXT, false);
		for (int i = 0; i < values.length * 2; i++) {
			Assert.assertTrue(reader.next());
			Assert.assertEquals(values[i % values.length], reader.value());
			Assert.assertEquals(i + 1, reader.numRead());
		}
		Assert.assertTrue(!reader.next());
		reader.close();
	}

	public void testReadSequenceFile() throws IOException {
		FolderWriter writer = new FolderWriter(new Path(filename + ".sf"), Text.class, Text.class);
		Text key = new Text();
		Text value = new Text();
		for (int i = 0; i < values.length; i++) {
			key.set(keys[i]);
			value.set(values[i]);
			writer.append(key, value);
		}
		writer.close();

		RecordReader reader = new RecordReader(filename + ".sf", "UTF-8", RecordWriter.TYPE_SEQUENCE_FILE, false);
		for (int i = 0; i < values.length; i++) {
			Assert.assertTrue(reader.next());
			Assert.assertEquals(keys[i], reader.key());
			Assert.assertEquals(values[i], reader.value());
			Assert.assertEquals(i + 1, reader.numRead());
		}
		Assert.assertTrue(!reader.next());
		reader.close();
	}

	public void testDetectFs() {
		Assert.assertEquals(true, RecordReader.detectFs("/hdfs/user/sxc"));
		Assert.assertEquals(true, RecordReader.detectFs("dfs://nlphead:6060/user/sxc"));
		Assert.assertEquals(true, RecordReader.detectFs("hdfs://nlphead:6060/user/sxc"));
		Assert.assertEquals(false, RecordReader.detectFs("/tmp/a.txt"));
		Assert.assertEquals(false, RecordReader.detectFs("b.sxc.zip"));
	}

	public void testDetectType() {
		Assert.assertEquals(RecordReader.TYPE_GZIPPED_TEXT, RecordReader.detectType("a.txt.gz"));
		Assert.assertEquals(RecordReader.TYPE_ZIPPED_TEXT, RecordReader.detectType("a.txt.zip"));
		Assert.assertEquals(RecordReader.TYPE_SEQUENCE_FILE, RecordReader.detectType("a.sf"));
		Assert.assertEquals(RecordReader.TYPE_PLAIN_TEXT, RecordReader.detectType("a.txt"));
		Assert.assertEquals(RecordReader.TYPE_PLAIN_TEXT, RecordReader.detectType("file-name-without-extension"));
	}
}
