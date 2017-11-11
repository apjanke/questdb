package com.questdb.net.udp.receiver;

import com.questdb.cairo.CairoConfiguration;
import com.questdb.cairo.CairoException;
import com.questdb.cairo.TableWriter;
import com.questdb.cairo.pool.ResourcePool;
import com.questdb.log.Log;
import com.questdb.log.LogFactory;
import com.questdb.misc.NetFacade;
import com.questdb.misc.Os;
import com.questdb.misc.Unsafe;
import com.questdb.mp.Job;
import com.questdb.parser.lp.CairoLineProtoParser;
import com.questdb.parser.lp.LineProtoLexer;
import com.questdb.std.str.DirectByteCharSequence;

import java.io.Closeable;

public class GenericLineProtoReceiver implements Closeable, Job {
    private static final Log LOG = LogFactory.getLog(GenericLineProtoReceiver.class);

    private final DirectByteCharSequence byteSequence = new DirectByteCharSequence();
    private final LineProtoLexer lexer;
    private final CairoLineProtoParser parser;
    private final NetFacade nf;
    private final int bufLen;
    private long fd = -1;
    private int commitRate;
    private long totalCount = 0;
    private long buf;

    public GenericLineProtoReceiver(ReceiverConfiguration receiverCfg, CairoConfiguration cairoCfg, ResourcePool<TableWriter> writerPool) {

        nf = receiverCfg.getNetFacade();

        fd = nf.socketUdp();
        if (fd < 0) {
            int errno = Os.errno();
            LOG.error().$("cannot open UDP socket [errno=").$(errno).$(']').$();
            throw CairoException.instance(errno).put("Cannot open UDP socket");
        }

        try {
            if (!nf.bind(fd, receiverCfg.getBindIPv4Address(), receiverCfg.getPort())) {
                int errno = Os.errno();
                LOG.error().$("cannot bind socket [errno=").$(errno).$(", fd=").$(fd).$(", bind=").$(receiverCfg.getBindIPv4Address()).$(", port=").$(receiverCfg.getPort()).$(']').$();
                throw CairoException.instance(Os.errno()).put("Cannot bind to ").put(receiverCfg.getBindIPv4Address()).put(':').put(receiverCfg.getPort());
            }

            if (!nf.join(fd, receiverCfg.getBindIPv4Address(), receiverCfg.getGroupIPv4Address())) {
                int errno = Os.errno();
                LOG.error().$("cannot join group [errno=").$(errno).$(", fd=").$(fd).$(", bind=").$(receiverCfg.getBindIPv4Address()).$(", group=").$(receiverCfg.getGroupIPv4Address()).$(']').$();
                throw CairoException.instance(Os.errno()).put("Cannot join group ").put(receiverCfg.getGroupIPv4Address()).put(" [bindTo=").put(receiverCfg.getBindIPv4Address()).put(']');
            }
        } catch (CairoException e) {
            close();
            throw e;
        }

        this.commitRate = receiverCfg.getCommitRate();

        if (receiverCfg.getReceiveBufferSize() != -1 && nf.setRcvBuf(fd, receiverCfg.getReceiveBufferSize()) != 0) {
            LOG.error().$("cannot set receive buffer size [fd=").$(fd).$(", size=").$(receiverCfg.getReceiveBufferSize()).$(']').$();
        }

        this.buf = Unsafe.malloc(this.bufLen = receiverCfg.getMsgBufferSize());

        lexer = new LineProtoLexer(receiverCfg.getMsgBufferSize());
        parser = new CairoLineProtoParser(cairoCfg, writerPool);
        lexer.withParser(parser);

        LOG.info().$("started [fd=").$(fd).$(", bind=").$(receiverCfg.getBindIPv4Address()).$(", group=").$(receiverCfg.getGroupIPv4Address()).$(", port=").$(receiverCfg.getPort()).$(", commitRate=").$(commitRate).$(']').$();
    }

    @Override
    public void close() {
        if (fd > -1) {
            nf.close(fd);
            if (buf != 0) {
                Unsafe.free(buf, bufLen);
            }
            if (parser != null) {
                parser.commitAll();
                parser.close();
            }
            LOG.info().$("closed [fd=").$(fd).$(']').$();
            fd = -1;
        }
    }

    @Override
    public boolean run() {
        boolean ran = false;
        int count;
        while ((count = nf.recv(fd, buf, bufLen)) > 0) {
            byteSequence.of(buf, buf + count);
            lexer.parse(byteSequence);
            lexer.parseLast();

            totalCount++;

            if (totalCount > commitRate) {
                totalCount = 0;
                parser.commitAll();
            }

            if (ran) {
                continue;
            }

            ran = true;
        }
        parser.commitAll();
        return ran;
    }
}
