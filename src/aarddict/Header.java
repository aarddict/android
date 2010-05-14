/**
 * 
 */
package aarddict;

import java.io.IOException;
import java.util.UUID;

public final class Header {

    public final String signature;
    public final String sha1sum;
    public final UUID   uuid;
    public final int    version;
    public final int    volume;
    public final int    of;
    public final long   metaLength;
    public final long   indexCount;
    public final long   articleOffset;
    public final String index1ItemFormat;
    public final String keyLengthFormat;
    public final String articleLengthFormat;
    public final long   index1Offset;
    public final long   index2Offset;

    Header(RandomAccessFile file) throws IOException {
        int specLen = 0;
        this.signature = file.readUTF8(4);
        specLen += 4;

        this.sha1sum = file.readUTF8(40);
        specLen += 40;

        this.version = file.readUnsignedShort();
        specLen += 2;

        this.uuid = file.readUUID();
        specLen += 16;

        this.volume = file.readUnsignedShort();
        specLen += 2;

        this.of = file.readUnsignedShort();
        specLen += 2;

        this.metaLength = file.readUnsignedInt();
        specLen += 4;

        this.indexCount = file.readUnsignedInt();
        specLen += 4;

        this.articleOffset = file.readUnsignedInt();
        specLen += 4;

        this.index1ItemFormat = file.readUTF8(4);
        specLen += 4;

        this.keyLengthFormat = file.readUTF8(2);
        specLen += 2;

        this.articleLengthFormat = file.readUTF8(2);
        specLen += 2;

        this.index1Offset = specLen + this.metaLength;
        this.index2Offset = this.index1Offset + this.indexCount * 8;
    }
}