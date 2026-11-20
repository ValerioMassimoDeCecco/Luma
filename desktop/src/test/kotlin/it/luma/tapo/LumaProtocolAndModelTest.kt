package it.luma.tapo

import it.luma.tapo.model.LumaColorMath
import it.luma.tapo.model.LumaException
import it.luma.tapo.protocol.P256Curve
import it.luma.tapo.protocol.Spake2Plus
import it.luma.tapo.protocol.TapoCrypto
import it.luma.tapo.protocol.TapoDiscoveryEngine
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class LumaProtocolAndModelTest {

    @Test
    fun `test private IPv4 and subnet prefix validation`() {
        assertTrue(LumaColorMath.isPrivateIPv4("192.168.1.20"))
        assertTrue(LumaColorMath.isPrivateIPv4("10.0.0.15"))
        assertTrue(LumaColorMath.isPrivateIPv4("172.16.5.10"))
        assertFalse(LumaColorMath.isPrivateIPv4("192.168.1.0"))
        assertFalse(LumaColorMath.isPrivateIPv4("192.168.1.255"))
        assertFalse(LumaColorMath.isPrivateIPv4("8.8.8.8"))

        assertTrue(LumaColorMath.isValidSubnetPrefix("192.168.1"))
        assertTrue(LumaColorMath.isValidSubnetPrefix("10.0.0"))
        assertTrue(LumaColorMath.isValidSubnetPrefix("172.16.4"))
        assertFalse(LumaColorMath.isValidSubnetPrefix("8.8.8"))
        assertFalse(LumaColorMath.isValidSubnetPrefix("192.168.1.20"))
    }

    @Test
    fun `test UDP discovery payload parser`() {
        val sampleJson = """
            {"result":{"device_id":"abc","device_model":"L530E(EU)","ip":"192.168.1.45","mac":"AA-BB-CC-DD-EE-FF","mgt_encrypt_schm":{"encrypt_type":"KLAP"}}}
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val parsed = TapoDiscoveryEngine.parseUdpDiscoveryPayload(
            senderIp = "192.168.1.45",
            rawBytes = sampleJson,
            existingHouseIps = setOf("192.168.1.99")
        )
        assertNotNull(parsed)
        assertEquals("192.168.1.45", parsed?.ip)
        assertEquals("l530", parsed?.model)
        assertEquals("KLAP v2", parsed?.protocol)
        assertEquals("AA-BB-CC-DD-EE-FF", parsed?.mac)
        assertFalse(parsed?.alreadyInHouse ?: true)

        val nonTapo = """{"hello":"world"}""".toByteArray(Charsets.UTF_8)
        assertNull(TapoDiscoveryEngine.parseUdpDiscoveryPayload("192.168.1.50", nonTapo, emptySet()))
    }

    @Test
    fun `test AES-128-CCM encrypt and decrypt round trip`() {
        val key = TapoCrypto.unhex("000102030405060708090a0b0c0d0e0f")
        val nonce = TapoCrypto.unhex("a0a1a2a3a4a5a6a7a8a9aaab")
        val plain = """{"method":"set_device_info","params":{"device_on":true,"brightness":72}}""".toByteArray(Charsets.UTF_8)

        val encrypted = TapoCrypto.ccmEncrypt(key, nonce, plain)
        assertEquals(plain.size + 16, encrypted.size)

        val decrypted = TapoCrypto.ccmDecrypt(key, nonce, encrypted)
        assertArrayEquals(plain, decrypted)

        // Tamper with tag -> must throw bad_signature
        val tampered = encrypted.copyOf()
        tampered[tampered.lastIndex] = (tampered.last().toInt() xor 0x01).toByte()
        var failed = false
        try {
            TapoCrypto.ccmDecrypt(key, nonce, tampered)
        } catch (e: LumaException) {
            failed = e.code == "bad_signature"
        }
        assertTrue(failed)
    }

    @Test
    fun `test P256 curve M and N decompression and SPAKE2+ exchange`() {
        assertTrue(P256Curve.M.isOnCurve())
        assertTrue(P256Curve.N.isOnCurve())
        assertTrue(P256Curve.BASE.isOnCurve())

        val w0 = BigInteger.valueOf(123456789L)
        val w1 = BigInteger.valueOf(987654321L)
        val x = BigInteger.valueOf(42424242L)
        val y = BigInteger.valueOf(71717171L)

        // Simulate device share Y = y*G + w0*N
        val deviceSharePoint = P256Curve.BASE.multiply(y).add(P256Curve.N.multiply(w0))
        val deviceShareBytes = deviceSharePoint.toRawBytesUncompressed()
        assertEquals(65, deviceShareBytes.size)

        val proof = Spake2Plus.spakeProof(
            w0 = w0,
            w1 = w1,
            x = x,
            context = "test-context".toByteArray(Charsets.UTF_8),
            proverId = ByteArray(0),
            verifierId = ByteArray(0),
            deviceShare = deviceShareBytes
        )
        assertEquals(65, proof.share.size)
        assertEquals(32, proof.confirm.size)
        assertEquals(32, proof.expectedDevice.size)
        assertEquals(32, proof.sharedKey.size)
    }
}
