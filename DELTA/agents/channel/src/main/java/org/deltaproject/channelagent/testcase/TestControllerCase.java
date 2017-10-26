package org.deltaproject.channelagent.testcase;

import com.google.common.primitives.Longs;
import org.deltaproject.channelagent.utils.Utils;
import org.deltaproject.channelagent.dummy.DummyOF10;
import org.deltaproject.channelagent.dummy.DummyOF13;
import org.deltaproject.channelagent.dummy.DummySwitch;
import org.projectfloodlight.openflow.exceptions.OFParseError;
import org.projectfloodlight.openflow.protocol.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Field;

/**
 * Created by seungsoo on 9/3/16.
 */
public class TestControllerCase {
    private static final Logger log = LoggerFactory.getLogger(TestControllerCase.class);

    private DummySwitch ofSwitch;
    private DummySwitch temp;
    private String targetIP;
    private String targetPORT;

    private long requestXid = 0xeeeeeeeel;
    private OFMessage response;
    private byte targetOFVersion;

    private Process proc;
    private int pid;

    public TestControllerCase(String ip, byte ver, String port) {
        targetIP = ip;
        targetPORT = port;
        targetOFVersion = ver;
    }

    public boolean isHandshaked() {
        return ofSwitch.getHandshaked();
    }

    public boolean startSW(int type) {
        log.info("[Channel Agent] Start Dummy Switch");

        ofSwitch = new DummySwitch();
        ofSwitch.setTestHandShakeType(type);
        ofSwitch.setOFFactory(targetOFVersion);
        ofSwitch.connectTargetController(targetIP, targetPORT);

        try {
            ofSwitch.sendHello(0);
        } catch (OFParseError ofParseError) {
            ofParseError.printStackTrace();
        }

        ofSwitch.start();

        if (type == DummySwitch.HANDSHAKE_DEFAULT) {
            while (!isHandshaked()) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            log.info("[Channel Agent] Handshake completed");
        }

        return true;
    }

    public void stopSW() {
        if (ofSwitch != null)
            ofSwitch.interrupt();

        log.info("[Channel Agent] Stop Dummny Switch");
    }

    public void stopTempSW() {
        if (temp != null)
            temp.interrupt();

        log.info("[Channel Agent] Stop Sub Switch");
    }

    public String testMalformedVersionNumber(String code) {
        log.info("[Channel Agent] " + code + " - Malformed Version Number test");

        while (!isHandshaked()) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        String result;

        byte[] msg;
        if (targetOFVersion == 4) {
            msg = Utils.hexStringToByteArray(DummyOF13.PACKET_IN);
            msg[0] = (byte) 0x01;
            result = "Send Packet-In msg with OF version 1.0\n";
        } else {
            msg = Utils.hexStringToByteArray(DummyOF10.PACKET_IN);
            msg[0] = (byte) 0x04;
            result = "Send Packet-In msg with OF version 1.3\n";
        }

        byte[] xidbytes = Longs.toByteArray(requestXid);
        System.arraycopy(xidbytes, 4, msg, 4, 4);

        ofSwitch.sendRawMsg(msg);

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        OFMessage response = ofSwitch.getResponse();
        if (response != null) {
            if (response.getType() == OFType.PACKET_OUT)
                result += "Response msg : " + response.toString() + ", FAIL";
            else
                result += "Response msg : " + response.toString() + ", PASS";
        } else
            result += "Response is NULL (expected msg is ERR), FAIL";

        stopSW();
        return result;
    }

    public String testCorruptedControlMsgType(String code) {
        log.info("[Channel Agent] " + code + " - Corrupted Control Message Type test");

        while (!isHandshaked()) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        String result = "Send a packet-in message with unknown message type";
        log.info("[Channel Agent] " + result);
        result = result + '\n';

        byte[] msg;
        if (targetOFVersion == 4) {
            msg = Utils.hexStringToByteArray(DummyOF13.PACKET_IN);
        } else {
            msg = Utils.hexStringToByteArray(DummyOF10.PACKET_IN);
        }

        msg[1] = (byte) 0xcc;   // malformed msg type
        byte[] xidbytes = Longs.toByteArray(requestXid);
        System.arraycopy(xidbytes, 4, msg, 4, 4);

        ofSwitch.sendRawMsg(msg);

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // switch disconnection
        OFMessage response = ofSwitch.getResponse();
        if (response != null) {
            result += "Response msg : " + response.toString() + ", PASS";
        } else
            result += "Response is NULL (expected msg is ERR), FAIL";

        stopSW();
        return result;
    }

    public String testHandShakeWithoutHello(String code) {
        log.info("[Channel Agent] " + code + " - Handshake without Hello Message test");
        startSW(DummySwitch.HANDSHAKE_NO_HELLO);

        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (this.ofSwitch.getHandshaked())
            return "switchConnected";
        else
            return "switchNotConnected";
    }

    public String testControlMsgBeforeHello(String code) {
        log.info("[Channel Agent] " + code + " - Control Message before Hello Message (Main Connection) test");

        String result = "Send a packet-in message before handshake";
        log.info("[Channel Agent] " + result);
        result = result + '\n';

        byte[] msg;
        if (targetOFVersion == 4) {
            msg = Utils.hexStringToByteArray(DummyOF13.PACKET_IN);
        } else {
            msg = Utils.hexStringToByteArray(DummyOF10.PACKET_IN);
        }

        byte[] xidbytes = Longs.toByteArray(requestXid);
        System.arraycopy(xidbytes, 4, msg, 4, 4);

        ofSwitch.sendRawMsg(msg);

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // switch disconnection
        OFMessage response = ofSwitch.getResponse();
        if (response != null) {
            result += "Response msg : " + response.toString() + ", FAIL";
        } else
            result += "Response is ignored, PASS";

        stopSW();
        return result;
    }

    public String testMultipleMainConnectionReq(String code) {
        log.info("[Channel Agent] " + code + " - Multiple Main Connection Request test");

        while (!isHandshaked()) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        log.info("Start another dummy switch");
        temp = new DummySwitch();
        temp.setTestHandShakeType(DummySwitch.HANDSHAKE_DEFAULT);
        temp.setOFFactory(targetOFVersion);
        temp.connectTargetController(targetIP, targetPORT);
        try {
            temp.sendHello(0);
        } catch (OFParseError ofParseError) {
            ofParseError.printStackTrace();
        }
        temp.start();

        return "Start another dummy switch";
    }

    public String testUnFlaggedFlowRemoveMsgNotification(String code) throws InterruptedException {
        log.info("[Channel Agent] " + code + " - no-flagged Flow Remove Message notification test");

        String result = "Send a un-flagged flow remove msg";
        log.info("[Channel Agent] " + result);
        result = result + '\n';

        while (!isHandshaked()) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        OFFlowAdd fa = ofSwitch.getBackupFlowAdd();
        if (fa == null)
            return "nothing";

        OFFlowRemoved.Builder fm = ofSwitch.getFactory().buildFlowRemoved();
        fm.setMatch(fa.getMatch());
        fm.setXid(this.requestXid);
        fm.setReason((short) 1);

        OFFlowRemoved msg = fm.build();
        ofSwitch.sendMsg(msg, -1);

        OFMessage response = ofSwitch.getResponse();
        if (response != null) {
            result += response.toString() + ", SUCCESS";
        } else
            result += ("response is null, FAIL");

        return result;
    }

    public String testTLSSupport(String code) {
        log.info("Test TLS Support");
        try {
            proc = Runtime.getRuntime().exec("python $HOME/test-controller-topo.py " + targetIP + " " + targetPORT);
            Field pidField = Class.forName("java.lang.UNIXProcess").getDeclaredField("pid");
            pidField.setAccessible(true);
            Object value = pidField.get(proc);
            this.pid = (Integer) value;

            log.info("TLS " + String.valueOf(pid));
        } catch (Exception e) {
            e.printStackTrace();
        }

        return "success";
    }

    public void exitTopo() {
        log.info("Exit test topology - ");
        try {
            Runtime.getRuntime().exec("sudo kill -9 " + this.pid);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
