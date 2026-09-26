package com.example.myapplication

class Calculator {
    enum class EvalError {
        DIVISION_BY_ZERO,
        INVALID_EXPRESSION,
        UNEXPECTED_TOKEN
    }

    fun evaluate(expression: String): String {
        if (expression.isBlank()) return ""
        return try {
            val result = evaluateExpression(expression)
            formatResult(result)
        } catch (e: ArithmeticException) {
            "DIV_ZERO"
        } catch (e: IllegalArgumentException) {
            "INVALID"
        } catch (e: Exception) {
            "INVALID"
        }
    }

    private fun evaluateExpression(expression: String): Double{
        val sanitized = expression
            .replace("×", "*")
            .replace("÷", "/")
            .replace(" ", "")
        return ExpressionEvaluator.eval(sanitized)
    }

    private fun formatResult(result: Double): String{
        return if (result == result.toLong().toDouble()) {
            result.toLong().toString()
        } else {
            result.toString()
        }

    }
}