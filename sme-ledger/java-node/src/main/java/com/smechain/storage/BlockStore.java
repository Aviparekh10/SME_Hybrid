package com.smechain.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smechain.chain.Block;
import com.smechain.crypto.CanonicalJson;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

public class BlockStore {
    private final Path dir;
    private final Path logFile;
    private final ObjectMapper mapper = CanonicalJson.MAPPER;
    private final FileChannel channel;

    // in-memory index: height -> file offset (line number)
    private final List<Long> offsets = new ArrayList<>();

    public BlockStore(Path dir) throws IOException {
        this.dir = dir;
        Files.createDirectories(dir);
        this.logFile = dir.resolve("blocks.log");
        if (!Files.exists(logFile)) Files.createFile(logFile);
        this.channel = FileChannel.open(logFile, StandardOpenOption.APPEND, StandardOpenOption.WRITE);
        rebuildIndex();
    }

    private void rebuildIndex() throws IOException {
        offsets.clear();
        try (RandomAccessFile raf = new RandomAccessFile(logFile.toFile(), "r")) {
            long pos = 0;
            while (true) {
                int b = raf.read();
                if (b == -1) break;
                offsets.add(pos);
                while (b != -1 && b != '\n') {
                    b = raf.read();
                }
                pos = raf.getFilePointer();
            }
        }
    }

    public synchronized void append(Block b) throws IOException {
        String json = mapper.writeValueAsString(b);
        long pos = channel.position();
        ByteBuffer buf = ByteBuffer.wrap((json + "\n").getBytes(StandardCharsets.UTF_8));
        while (buf.hasRemaining()) channel.write(buf);
        channel.force(true);
        offsets.add(pos);
    }

    public synchronized long height() {
        return offsets.size() - 1;
    }

    public synchronized Block getByHeight(long height) throws IOException {
        if (height < 0 || height >= offsets.size()) return null;
        try (RandomAccessFile raf = new RandomAccessFile(logFile.toFile(), "r")) {
            raf.seek(offsets.get(Math.toIntExact(height)));
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int b;
            while ((b = raf.read()) != -1 && b != '\n') baos.write(b);
            String line = baos.toString(StandardCharsets.UTF_8);
            if (line.isEmpty()) return null;
            return mapper.readValue(line, Block.class);
        }
    }

    public synchronized Block tip() throws IOException {
        if (offsets.isEmpty()) return null;
        return getByHeight(offsets.size() - 1L);
    }

    public synchronized List<Block> range(long startHeight, long max) throws IOException {
        List<Block> out = new ArrayList<>();
        long end = Math.min(offsets.size(), startHeight + max);
        for (long h = startHeight; h < end; h++) {
            Block b = getByHeight(h);
            if (b != null) out.add(b);
        }
        return out;
    }

    public Path getLogFile() { return logFile; }
}
