package bhuman.message;

import bhuman.message.data.Angle;
import bhuman.message.data.ComplexStreamReader;
import bhuman.message.data.Eigen;
import bhuman.message.data.NativeReaders;
import bhuman.message.data.Primitive;
import bhuman.message.data.SimpleStreamReader;
import bhuman.message.data.StreamedObject;
import bhuman.message.data.Timestamp;
import common.Log;
import data.SPLStandardMessage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import util.Unsigned;

/**
 *
 * @author Felix Thielke
 */
public class BHumanMessageParts {

    private static final String BHUMAN_STANDARD_MESSAGE_STRUCT_HEADER = "BHUM";
    private static final short BHUMAN_STANDARD_MESSAGE_STRUCT_VERSION = 6;

    private static final String BHUMAN_ARBITRARY_MESSAGE_STRUCT_HEADER = "BHUA";
    private static final short BHUMAN_ARBITRARY_MESSAGE_STRUCT_VERSION = 0;

    /**
     * The B-Human standard information transferred in this message.
     */
    public final BHumanStandardMessagePart bhuman;

    /**
     * The MessageQueue transferred in this message.
     */
    public final MessageQueue queue;

    public BHumanMessageParts(final SPLStandardMessage origin, final ByteBuffer data) {
        BHumanStandardMessagePart bhum = null;
        MessageQueue q = null;

        data.rewind();
        data.order(ByteOrder.LITTLE_ENDIAN);

        if (data.hasRemaining()) {
            String header = new NativeReaders.SimpleStringReader(4).read(data);
            short version = NativeReaders.ucharReader.read(data);
            if (header.equals(BHUMAN_STANDARD_MESSAGE_STRUCT_HEADER)) {
                if (version == BHUMAN_STANDARD_MESSAGE_STRUCT_VERSION) {
                    bhum = new BHumanStandardMessagePart();
                    if (bhum.getStreamedSize(data) <= data.remaining()) {
                        bhum.read(data);
                    } else {
                        Log.error("Wrong size of B-Human standard message struct: was " + data.remaining() + ", expected " + bhum.getStreamedSize(data));
                    }
                } else {
                    Log.error("Wrong B-Human standard message struct version: was " + version + ", expected " + BHUMAN_STANDARD_MESSAGE_STRUCT_VERSION);
                }
            } else {
                data.position(data.position() - 5);
                Log.error("Wrong B-Human standard message struct header");
            }
        }

        if (data.hasRemaining()) {
            String header = new NativeReaders.SimpleStringReader(4).read(data);
            short version = NativeReaders.ucharReader.read(data);
            if (header.equals(BHUMAN_ARBITRARY_MESSAGE_STRUCT_HEADER)) {
                if (version == BHUMAN_ARBITRARY_MESSAGE_STRUCT_VERSION) {
                    q = new MessageQueue(origin, data);
                } else {
                    Log.error("Wrong B-Human arbitrary message struct version: was " + version + ", expected " + BHUMAN_ARBITRARY_MESSAGE_STRUCT_VERSION);
                }
            } else {
                data.position(data.position() - 5);
                Log.error("Wrong B-Human arbitrary message struct header");
            }
        }

        this.bhuman = bhum;
        this.queue = q;
    }

    public static class BHumanStandardMessagePart implements ComplexStreamReader<BHumanStandardMessagePart> {

        private static final int BHUMAN_STANDARD_MESSAGE_MAX_NUM_OF_PLAYERS = 6;

        public static enum Role {
            undefined,
            keeper,
            attackingKeeper,
            striker,
            defender,
            supporter,
            penaltyStriker,
            penaltyKeeper,
            none
        }

        public static class BNTPMessage {

            public long requestOrigination;
            public long requestReceipt;
            public short receiver;
        }

        public static class Obstacle {
            public static enum Type {
                goalpost,
                unknown,
                someRobot,
                opponent,
                teammate,
                fallenSomeRobot,
                fallenOpponent,
                fallenTeammate
            }

            public float[] covariance = new float[3];
            public Eigen.Vector2f center = new Eigen.Vector2f();
            public Eigen.Vector2f left = new Eigen.Vector2f();
            public Eigen.Vector2f right = new Eigen.Vector2f();
            public Timestamp lastSeen;
            public Type type;
        }

        public short magicNumber;
        public long timestamp;
        public boolean isPenalized;
        public boolean isUpright;
        public boolean hasGroundContact;
        public Timestamp timeOfLastGroundContact;

        public float robotPoseValidity;
        public float robotPoseDeviation;
        public float[] robotPoseCovariance = new float[6];
        public Timestamp timestampLastJumped;

        public Timestamp ballTimeWhenLastSeen;
        public Timestamp ballTimeWhenDisappeared;
        public short ballSeenPercentage;
        public Eigen.Vector2f ballVelocity = new Eigen.Vector2f();
        public Eigen.Vector2f ballLastPercept = new Eigen.Vector2f();
        public float[] ballCovariance = new float[3];

        public byte confidenceOfLastWhistleDetection;
        public Timestamp lastTimeWhistleDetected;

        public Role role;
        public Timestamp timeWhenReachBall;
        public Timestamp timeWhenReachBallStriker;
        public int passTarget;
        public Eigen.Vector2f walkingTo = new Eigen.Vector2f();
        public Eigen.Vector2f shootingTo = new Eigen.Vector2f();

        public Role[] teammateRoles = new Role[BHUMAN_STANDARD_MESSAGE_MAX_NUM_OF_PLAYERS];
        public int captain;
        public Timestamp teammateRolesTimestamp;

        public List<Obstacle> obstacles;

        public boolean requestsNTPMessage;
        public List<BNTPMessage> ntpMessages;

        @Override
        public int getStreamedSize(final ByteBuffer stream) {
            int size = 1 // magicNumber
                    + 4 // timestamp
                    + 1 // timeOfLastGroundContact
                    + 1 // robotPoseValidity
                    + 4 // robotPoseDeviation
                    + 6 * 4 // robotPoseCovariance
                    + 1 // timestampLastJumped
                    + 4 // ballTimeWhenLastSeen
                    + 4 // ballTimeWhenDisappeared, ballSeenPercentage
                    + 2 * 2 // ballVelocity
                    + 2 * 2 // ballLastPercept
                    + 3 * 4 // ballCovariance
                    + 1 // confidenceOfLastWhistleDetection
                    + 2 // lastTimeWhistleDetected
                    + 4 // role, passTarget, teammateRoles
                    + 2 // timeWhenReachBall
                    + 2 // timeWhenReachBallStriker
                    + 2 * 2 // walkingTo
                    + 2 * 2 // shootingTo
                    + 2 // captain, teammateRolesTimestamp
                    + 2; // number of obstacles, isPenalized, isUpright, hasGroundContact, requestsNTPMessage, NTP reply bitset

            if (stream.remaining() < size) {
                return size;
            }

            final int container = Unsigned.toUnsigned(stream.getShort(stream.position() + size - 2));

            int ntpReceivers = container & 0x3F;
            int ntpCount = 0;
            while (ntpReceivers != 0) {
                if ((ntpReceivers & 1) == 1) {
                    ntpCount++;
                }
                ntpReceivers >>= 1;
            }

            final int obstacleCount = container >> 10;

            return size
                    + obstacleCount * 25
                    + ntpCount * 5;
        }

        @Override
        public BHumanStandardMessagePart read(final ByteBuffer stream) {
            magicNumber = Unsigned.toUnsigned(stream.get());
            timestamp = Unsigned.toUnsigned(stream.getInt());

            timeOfLastGroundContact = new Timestamp(timestamp - (((long) Unsigned.toUnsigned(stream.get())) << 6));

            robotPoseValidity = ((float) Unsigned.toUnsigned(stream.get())) / 255.f;
            robotPoseDeviation = stream.getFloat();
            for (int i = 0; i < 6; ++i) {
                robotPoseCovariance[i] = stream.getFloat();
            }
            timestampLastJumped = new Timestamp(timestamp - (((long) Unsigned.toUnsigned(stream.get())) << 7));

            ballTimeWhenLastSeen = new Timestamp().read(stream);
            final int ballTimeWhenDisappearedSeenPercentage = stream.getInt();
            ballTimeWhenDisappeared = new Timestamp(ballTimeWhenDisappearedSeenPercentage & 0x00FFFFFF);
            ballSeenPercentage = Unsigned.toUnsigned((byte) ((ballTimeWhenDisappearedSeenPercentage >> 24) & 0xFF));
            ballVelocity.x = (float) stream.getShort();
            ballVelocity.y = (float) stream.getShort();
            ballLastPercept.x = (float) stream.getShort();
            ballLastPercept.y = (float) stream.getShort();
            for (int i = 0; i < 3; ++i) {
                ballCovariance[i] = stream.getFloat();
            }

            confidenceOfLastWhistleDetection = stream.get();
            lastTimeWhistleDetected = new Timestamp(timestamp - (long) Unsigned.toUnsigned(stream.getShort()));

            final int rolePassTargetTeammateRolesContainer = stream.getInt();
            for (int i = 0; i < BHUMAN_STANDARD_MESSAGE_MAX_NUM_OF_PLAYERS; ++i) {
                teammateRoles[i] = Role.values()[(rolePassTargetTeammateRolesContainer >> ((BHUMAN_STANDARD_MESSAGE_MAX_NUM_OF_PLAYERS - i - 1) * 4)) & 0xF];
            }
            passTarget = (rolePassTargetTeammateRolesContainer >> 24) & 0xF;
            if (passTarget == 15) {
                passTarget = -1;
            }
            role = Role.values()[(rolePassTargetTeammateRolesContainer >> 28) & 0xF];
            timeWhenReachBall = new Timestamp(timestamp + (((long) Unsigned.toUnsigned(stream.getShort())) << 3));
            timeWhenReachBallStriker = new Timestamp(timestamp + (((long) Unsigned.toUnsigned(stream.getShort())) << 3));
            walkingTo.x = (float) stream.getShort();
            walkingTo.y = (float) stream.getShort();
            shootingTo.x = (float) stream.getShort();
            shootingTo.y = (float) stream.getShort();

            final short captainTeammateRolesTimestamp = stream.getShort();
            captain = (captainTeammateRolesTimestamp >> 13) & 0x7;
            if (captain == 7) {
                captain = -1;
            }
            teammateRolesTimestamp = new Timestamp(timestamp - (long) (captainTeammateRolesTimestamp & 0x1FFF));

            final int boolAndNTPReceiptContainer = Unsigned.toUnsigned(stream.getShort());
            long runner = 1 << 9;
            isPenalized = (boolAndNTPReceiptContainer & runner) != 0;
            isUpright = (boolAndNTPReceiptContainer & (runner >>= 1)) != 0;
            hasGroundContact = (boolAndNTPReceiptContainer & (runner >>= 1)) != 0;
            requestsNTPMessage = (boolAndNTPReceiptContainer & (runner >>= 1)) != 0;
            final int numOfObstacles = (boolAndNTPReceiptContainer >> 10) & 0x3F;
            obstacles = new ArrayList<>(numOfObstacles);
            for (int i = 0; i < numOfObstacles; ++i) {
                final Obstacle obstacle = new Obstacle();

                for (int j = 0; j < 3; ++j) {
                    obstacle.covariance[j] = stream.getFloat();
                }
                obstacle.center.x = (float) stream.getShort();
                obstacle.center.y = (float) stream.getShort();
                final short leftX_4Type = stream.getShort();
                final short leftY_4Type = stream.getShort();
                final short rightX_4Type = stream.getShort();
                final short rightY_4Type = stream.getShort();
                obstacle.left.x = (float) ((short) (leftX_4Type << 2));
                obstacle.left.y = (float) ((short) (leftY_4Type << 2));
                obstacle.right.x = (float) ((short) (rightX_4Type << 2));
                obstacle.right.y = (float) ((short) (rightY_4Type << 2));
                obstacle.lastSeen = new Timestamp(timestamp - (((long) Unsigned.toUnsigned(stream.get())) << 6));

                obstacle.type = Obstacle.Type.values()[((leftX_4Type >> 14) & 0x3) | ((leftY_4Type >> 12) & 0xC) | ((rightX_4Type >> 10) & 0x30) | ((rightY_4Type >> 8) & 0xC0)];

                obstacles.add(obstacle);
            }
            ntpMessages = new LinkedList<>();
            for (short i = 1; runner != 0; ++i) {
                if ((boolAndNTPReceiptContainer & (runner >>= 1)) != 0) {
                    final BNTPMessage message = new BNTPMessage();
                    message.receiver = i;
                    ntpMessages.add(message);
                    final long timeStruct32 = Unsigned.toUnsigned(stream.getInt());
                    final long timeStruct8 = (long) Unsigned.toUnsigned(stream.get());

                    message.requestOrigination = timeStruct32 & 0xFFFFFFF;
                    message.requestReceipt = timestamp - ((timeStruct32 >> 20) & 0xF00) | timeStruct8;
                }
            }

            return this;
        }
    }
}
