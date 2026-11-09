package dev.pepotech.pepomote.control

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import kotlin.math.abs

/**
 * Mando físico conectado al móvil (p. ej. GameSir X5 Lite): sus botones y
 * sticks se escriben en [ButtonState], igual que la pantalla táctil.
 * Mapa tipo Xbox: A/B/X/Y, LB/RB, LT/RT, Back/Start, L3/R3, Guide y cruceta.
 */
object PhysicalGamepad {
    private const val DEAD = 0.12f
    private const val TRIGGER_ON = 0.5f

    private var lastL2 = false
    private var lastR2 = false
    private var hatMask = 0

    private fun isPadSource(source: Int): Boolean =
        source and (InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_JOYSTICK or InputDevice.SOURCE_DPAD) != 0

    private fun bitFor(keyCode: Int): Int = when (keyCode) {
        KeyEvent.KEYCODE_BUTTON_A -> ButtonState.A
        KeyEvent.KEYCODE_BUTTON_B -> ButtonState.B
        KeyEvent.KEYCODE_BUTTON_X -> ButtonState.X
        KeyEvent.KEYCODE_BUTTON_Y -> ButtonState.Y
        KeyEvent.KEYCODE_BUTTON_L1 -> ButtonState.L
        KeyEvent.KEYCODE_BUTTON_R1 -> ButtonState.R
        KeyEvent.KEYCODE_BUTTON_L2 -> ButtonState.ZL
        KeyEvent.KEYCODE_BUTTON_R2 -> ButtonState.ZR
        KeyEvent.KEYCODE_BUTTON_SELECT -> ButtonState.MINUS
        KeyEvent.KEYCODE_BUTTON_START -> ButtonState.PLUS
        KeyEvent.KEYCODE_BUTTON_MODE -> ButtonState.HOME
        KeyEvent.KEYCODE_BUTTON_THUMBL -> ButtonState.STICK_L
        KeyEvent.KEYCODE_BUTTON_THUMBR -> ButtonState.STICK_R
        KeyEvent.KEYCODE_DPAD_UP -> ButtonState.DPAD_UP
        KeyEvent.KEYCODE_DPAD_DOWN -> ButtonState.DPAD_DOWN
        KeyEvent.KEYCODE_DPAD_LEFT -> ButtonState.DPAD_LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> ButtonState.DPAD_RIGHT
        else -> 0
    }

    /** true = el evento era del mando y ya está atendido. */
    fun onKey(e: KeyEvent): Boolean {
        if (!isPadSource(e.source)) return false
        val bit = bitFor(e.keyCode)
        if (bit == 0) return false
        when (e.action) {
            KeyEvent.ACTION_DOWN -> if (e.repeatCount == 0) ButtonState.set(bit, true)
            KeyEvent.ACTION_UP -> ButtonState.set(bit, false)
        }
        return true
    }

    private fun dz(v: Float): Float = if (abs(v) < DEAD) 0f else v

    private fun biggest(a: Float, b: Float): Float = if (abs(a) >= abs(b)) a else b

    /** Sticks, gatillos analógicos y cruceta en forma de eje (HAT). */
    fun onMotion(e: MotionEvent): Boolean {
        if (e.action != MotionEvent.ACTION_MOVE) return false
        if (e.source and InputDevice.SOURCE_JOYSTICK == 0) return false

        val lx = dz(e.getAxisValue(MotionEvent.AXIS_X))
        val ly = dz(e.getAxisValue(MotionEvent.AXIS_Y))
        // El stick derecho llega como Z/RZ en casi todos los mandos y como RX/RY en otros
        val rx = dz(biggest(e.getAxisValue(MotionEvent.AXIS_Z), e.getAxisValue(MotionEvent.AXIS_RX)))
        val ry = dz(biggest(e.getAxisValue(MotionEvent.AXIS_RZ), e.getAxisValue(MotionEvent.AXIS_RY)))
        // En Android +Y es hacia abajo; en ButtonState +Y es hacia arriba
        ButtonState.setStick((lx * 127).toInt(), (-ly * 127).toInt())
        ButtonState.setStick2((rx * 127).toInt(), (-ry * 127).toInt())

        val l2 = maxOf(e.getAxisValue(MotionEvent.AXIS_LTRIGGER), e.getAxisValue(MotionEvent.AXIS_BRAKE)) > TRIGGER_ON
        val r2 = maxOf(e.getAxisValue(MotionEvent.AXIS_RTRIGGER), e.getAxisValue(MotionEvent.AXIS_GAS)) > TRIGGER_ON
        if (l2 != lastL2) { lastL2 = l2; ButtonState.set(ButtonState.ZL, l2) }
        if (r2 != lastR2) { lastR2 = r2; ButtonState.set(ButtonState.ZR, r2) }

        val hx = e.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hy = e.getAxisValue(MotionEvent.AXIS_HAT_Y)
        var hat = 0
        if (hx < -0.5f) hat = hat or ButtonState.DPAD_LEFT
        if (hx > 0.5f) hat = hat or ButtonState.DPAD_RIGHT
        if (hy < -0.5f) hat = hat or ButtonState.DPAD_UP
        if (hy > 0.5f) hat = hat or ButtonState.DPAD_DOWN
        if (hat != hatMask) {
            val changed = hat xor hatMask
            for (bit in intArrayOf(ButtonState.DPAD_UP, ButtonState.DPAD_DOWN, ButtonState.DPAD_LEFT, ButtonState.DPAD_RIGHT)) {
                if (changed and bit != 0) ButtonState.set(bit, hat and bit != 0)
            }
            hatMask = hat
        }
        return true
    }
}

