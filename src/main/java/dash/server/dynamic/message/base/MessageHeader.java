package dash.server.dynamic.message.base;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dash.server.dynamic.message.exception.MessageException;
import util.module.ByteUtil;

import java.nio.charset.StandardCharsets;

public class MessageHeader {

    ////////////////////////////////////////////////////////////
    public static final int SIZE = 22;

    private final String magicCookie;               // 2 bytes
    private final MessageType messageType;     // 4 bytes
    private final int seqNumber;                  // 4 bytes
    private final long timeStamp;                   // 8 bytes
    private int bodyLength = 0;                     // 4 bytes
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public MessageHeader(byte[] data) throws MessageException {
        if (data.length >= SIZE) {
            int index = 0;

            byte[] magicCookieByteData = new byte[2];
            System.arraycopy(data, index, magicCookieByteData, 0, magicCookieByteData.length);
            this.magicCookie = new String(magicCookieByteData, StandardCharsets.UTF_8);
            index += magicCookieByteData.length;

            byte[] messageTypeByteData = new byte[ByteUtil.NUM_BYTES_IN_INT];
            System.arraycopy(data, index, messageTypeByteData, 0, messageTypeByteData.length);
            this.messageType = MessageType.values()[ByteUtil.bytesToInt(messageTypeByteData, true)];
            index += messageTypeByteData.length;

            byte[] seqNumberByteData = new byte[ByteUtil.NUM_BYTES_IN_INT];
            System.arraycopy(data, index, seqNumberByteData, 0, seqNumberByteData.length);
            this.seqNumber = ByteUtil.bytesToInt(seqNumberByteData, true);
            index += seqNumberByteData.length;

            byte[] timeStampByteData = new byte[ByteUtil.NUM_BYTES_IN_LONG];
            System.arraycopy(data, index, timeStampByteData, 0, timeStampByteData.length);
            this.timeStamp = ByteUtil.bytesToShort(timeStampByteData, true);
            index += timeStampByteData.length;

            byte[] bodyLengthByteData = new byte[ByteUtil.NUM_BYTES_IN_INT];
            System.arraycopy(data, index, bodyLengthByteData, 0, bodyLengthByteData.length);
            this.bodyLength = ByteUtil.bytesToInt(bodyLengthByteData, true);
        } else {
            magicCookie = null;
            messageType = MessageType.UNKNOWN;
            seqNumber = 0;
            timeStamp = 0;
            bodyLength = 0;

            throw new MessageException("[URtspHeader] Fail to create the header. Data length is wrong. (" + data.length + ")");
        }
    }

    public MessageHeader(String magicCookie, MessageType messageType, int seqNumber, long timeStamp, int bodyLength) {
        this.magicCookie = magicCookie;
        this.messageType = messageType;
        this.seqNumber = seqNumber;
        this.timeStamp = timeStamp;
        this.bodyLength = bodyLength;
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public byte[] getByteData() {
        byte[] data = new byte[SIZE];

        int index = 0;
        byte[] magicCookieByteData = magicCookie.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(magicCookieByteData, 0, data, index, magicCookieByteData.length);
        index += magicCookieByteData.length;

        byte[] messageTypeByteData = ByteUtil.intToBytes(MessageType.valueOf(messageType.name()).ordinal(), true);
        System.arraycopy(messageTypeByteData, 0, data, index, messageTypeByteData.length);
        index += messageTypeByteData.length;

        byte[] seqNumberByteData = ByteUtil.intToBytes(seqNumber, true);
        System.arraycopy(seqNumberByteData, 0, data, index, ByteUtil.NUM_BYTES_IN_INT);
        index += seqNumberByteData.length;

        byte[] timeStampByteData = ByteUtil.longToBytes(timeStamp, true);
        System.arraycopy(timeStampByteData, 0, data, index, ByteUtil.NUM_BYTES_IN_LONG);
        index += timeStampByteData.length;

        byte[] bodyLengthByteData = ByteUtil.intToBytes(bodyLength, true);
        System.arraycopy(bodyLengthByteData, 0, data, index, bodyLengthByteData.length);

        return data;
    }
    ////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////
    public String getMagicCookie() {
        return magicCookie;
    }

    public MessageType getMessageType() {
        return messageType;
    }

    public int getSeqNumber() {
        return seqNumber;
    }

    public long getTimeStamp() {
        return timeStamp;
    }

    public void setBodyLength(int bodyLength) {
        this.bodyLength = bodyLength;
    }

    public int getBodyLength() {
        return bodyLength;
    }

    @Override
    public String toString() {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(this);
    }
    ////////////////////////////////////////////////////////////

}
