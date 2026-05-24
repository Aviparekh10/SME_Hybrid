package com.smechain.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smechain.chain.Block;
import com.smechain.crypto.CanonicalJson;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class BlockStore {
    private final Path dir;
    private final Path logFile;
    private final ObjectMapper mapper = CanonicalJson.MAPPER;

    // in-memory index: height -> file offset (line number)
    private final List<Long> offsets = new ArrayList<>();

    public BlockStore(Path dir) throws IOException {
        this.dir = dir;
        Files.createDirectories(dir);
        this.logFile = dir.resolve("blocks.log");
        if (!Files.exists(logFile)) Files.createFile(logFile);
        rebuildIndex();
    }

    private void rebuildIndex() throws IOException {
        offsets.clear();
        try (RandomAccessFile raf = new RandomAccessFile(logFile.toFile(), "r")) {
            long pos = 0;
            String line;
            while ((line = raf.readLine()) != null) {
                offsets.add(pos);
                pos = raf.getFilePointer();
            }
        }
    }

    public synchronized void append(Block b) throws IOException {
        String json = mapper.writeValueAsString(b);
        try (FileWriter fw = new FileWriter(logFile.toFile(), true)) {
            long pos = Files.size(logFile);
            fw.write(json);
            fw.write("\n");
            fw.flush();
            offsets.add(pos);
        }
    }

    public synchronized long height() {
        return offsets.size() - 1;
    }

    public synchronized Block getByHeight(long height) throws IOException {
        if (height < 0 || height >= offsets.size()) return null;
        try (RandomAccessFile raf = new RandomAccessFile(logFile.toFile(), "r")) {
            raf.seek(offsets.get((int)height));
            String line = raf.readLine();
            if (line == null) return null;
            return mapper.readValue(line, Block.class);
        }
    }

    public synchronized Block tip() throws IOException {
        if (offsets.isEmpty()) return null;
        return getByHeight(offsets.size()-1);
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
