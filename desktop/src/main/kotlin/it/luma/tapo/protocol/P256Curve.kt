package it.luma.tapo.protocol

import it.luma.tapo.model.LumaException
import java.math.BigInteger

/**
 * Pure Kotlin NIST P-256 (secp256r1) curve arithmetic for Tapo TPAP SPAKE2+ handshake.
 * Matches the noble-curves p256 implementation in protocol.js.
 */
object P256Curve {
    val P: BigInteger = BigInteger("ffffffff00000001000000000000000000000000ffffffffffffffffffffffff", 16)
    val A: BigInteger = P.subtract(BigInteger.valueOf(3))
    val B: BigInteger = BigInteger("5ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53b0f63bce3c3e27d2604b", 16)
    val ORDER: BigInteger = BigInteger("ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551", 16)

    private val GX = BigInteger("6b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296", 16)
    private val GY = BigInteger("4fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5", 16)

    // P-256 mod p has p ≡ 3 (mod 4), so sqrt(z) = z^((p+1)/4) mod p
    private val SQRT_EXP: BigInteger = P.add(BigInteger.ONE).shiftRight(2)
    private val TWO = BigInteger.valueOf(2)
    private val THREE = BigInteger.valueOf(3)

    data class Point(
        val x: BigInteger,
        val y: BigInteger,
        val isZero: Boolean = false
    ) {
        fun isOnCurve(): Boolean {
            if (isZero) return true
            if (x < BigInteger.ZERO || x >= P || y < BigInteger.ZERO || y >= P) return false
            val lhs = y.multiply(y).mod(P)
            val rhs = x.pow(3).add(A.multiply(x)).add(B).mod(P)
            return lhs == rhs
        }

        fun assertValidity() {
            if (isZero || !isOnCurve()) {
                throw LumaException("La lampadina ha inviato una chiave P-256 non valida.", "protocol")
            }
        }

        fun negate(): Point = if (isZero) this else Point(x, P.subtract(y).mod(P), false)

        fun add(other: Point): Point {
            if (this.isZero) return other
            if (other.isZero) return this
            if (this.x == other.x) {
                return if (this.y == other.y && this.y != BigInteger.ZERO) {
                    double()
                } else {
                    ZERO
                }
            }
            val dx = other.x.subtract(this.x).mod(P)
            val dy = other.y.subtract(this.y).mod(P)
            val slope = dy.multiply(dx.modInverse(P)).mod(P)
            val rx = slope.multiply(slope).subtract(this.x).subtract(other.x).mod(P)
            val ry = slope.multiply(this.x.subtract(rx)).subtract(this.y).mod(P)
            return Point(rx, ry, false)
        }

        fun subtract(other: Point): Point = add(other.negate())

        fun double(): Point {
            if (isZero || y == BigInteger.ZERO) return ZERO
            val num = THREE.multiply(x.multiply(x)).add(A).mod(P)
            val den = TWO.multiply(y).mod(P)
            val slope = num.multiply(den.modInverse(P)).mod(P)
            val rx = slope.multiply(slope).subtract(TWO.multiply(x)).mod(P)
            val ry = slope.multiply(x.subtract(rx)).subtract(y).mod(P)
            return Point(rx, ry, false)
        }

        fun multiply(scalar: BigInteger): Point {
            val k = scalar.mod(ORDER)
            if (k == BigInteger.ZERO || isZero) return ZERO
            var result = ZERO
            var addend = this
            val bitLen = k.bitLength()
            for (i in 0 until bitLen) {
                if (k.testBit(i)) {
                    result = result.add(addend)
                }
                addend = addend.double()
            }
            return result
        }

        fun toRawBytesUncompressed(): ByteArray {
            if (isZero) throw LumaException("Punto P-256 all'infinito non serializzabile.", "protocol")
            val out = ByteArray(65)
            out[0] = 0x04
            val xb = toFixed32(x)
            val yb = toFixed32(y)
            System.arraycopy(xb, 0, out, 1, 32)
            System.arraycopy(yb, 0, out, 33, 32)
            return out
        }
    }

    val ZERO = Point(BigInteger.ZERO, BigInteger.ZERO, isZero = true)
    val BASE = Point(GX, GY, isZero = false)

    // RFC 9383 SPAKE2+ P-256 M and N points
    val M: Point by lazy {
        fromHex("02886e2f97ace46e55ba9dd7242579f2993b64e16ef3dcab95afd497333d8fa12f")
    }
    val N: Point by lazy {
        fromHex("03d8bbd6c639c62937b04d997f38c3770719c629d7014d49a24b4f98baa1292b49")
    }

    fun toFixed32(n: BigInteger): ByteArray {
        val raw = n.toByteArray()
        val out = ByteArray(32)
        if (raw.size >= 32) {
            System.arraycopy(raw, raw.size - 32, out, 0, 32)
        } else {
            System.arraycopy(raw, 0, out, 32 - raw.size, raw.size)
        }
        return out
    }

    fun fromBytes(bytes: ByteArray): Point {
        if (bytes.isEmpty()) throw LumaException("La lampadina ha inviato una chiave P-256 non valida.", "protocol")
        return when (bytes[0].toInt() and 0xFF) {
            0x04 -> {
                if (bytes.size != 65) throw LumaException("La lampadina ha inviato una chiave P-256 non valida.", "protocol")
                val x = BigInteger(1, bytes.copyOfRange(1, 33))
                val y = BigInteger(1, bytes.copyOfRange(33, 65))
                Point(x, y, false).also { it.assertValidity() }
            }
            0x02, 0x03 -> {
                if (bytes.size != 33) throw LumaException("La lampadina ha inviato una chiave P-256 non valida.", "protocol")
                val isOdd = (bytes[0].toInt() and 1) == 1
                val x = BigInteger(1, bytes.copyOfRange(1, 33))
                if (x < BigInteger.ZERO || x >= P) throw LumaException("La lampadina ha inviato una chiave P-256 non valida.", "protocol")
                val rhs = x.pow(3).add(A.multiply(x)).add(B).mod(P)
                var y = rhs.modPow(SQRT_EXP, P)
                if (y.multiply(y).mod(P) != rhs) {
                    throw LumaException("La lampadina ha inviato una chiave P-256 non valida.", "protocol")
                }
                if (y.testBit(0) != isOdd) {
                    y = P.subtract(y).mod(P)
                }
                Point(x, y, false).also { it.assertValidity() }
            }
            else -> throw LumaException("La lampadina ha inviato una chiave P-256 non valida.", "protocol")
        }
    }

    fun fromHex(hex: String): Point {
        return fromBytes(TapoCrypto.unhex(hex))
    }
}
