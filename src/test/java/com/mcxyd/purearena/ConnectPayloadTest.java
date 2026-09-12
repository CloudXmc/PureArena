package com.mcxyd.purearena;

import com.mcxyd.purearena.service.LobbyConnectService;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BungeeCord Connect 报文编码测试。
 */
class ConnectPayloadTest {

    @Test
    void 报文包含Connect子频道和目标服务器名() throws Exception {
        byte[] payload = LobbyConnectService.buildConnectPayload("server");
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload))) {
            assertEquals("Connect", in.readUTF());
            assertEquals("server", in.readUTF());
            assertEquals(0, in.available());
        }
    }
}
