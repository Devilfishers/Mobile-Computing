package com.example.myapplication

/**
 * A mathematical expression evaluator supporting +, -, *, /, parentheses, and unary negation.
 * Uses recursive descent parsing with proper operator precedence.
 */
object ExpressionEvaluator {

    fun eval(expression: String): Double {
        if (expression.isBlank()) return 0.0
        
        val tokenizer = Tokenizer(expression)
        val result = parseExpression(tokenizer)
        if (tokenizer.hasMoreTokens()){
            throw IllegalArgumentException("Unexpected token ${tokenizer.peek()}")
        }
        return result
    }

    // Parses addition and subtraction (lowest precedence)
    private fun parseExpression(tokenizer: Tokenizer): Double {
        var result = parseTerm(tokenizer)
        
        while (tokenizer.hasMoreTokens()) {
            val operator = tokenizer.peek()
            when (operator) {
                "+" -> {
                    tokenizer.consume()
                    result += parseTerm(tokenizer)
                }
                "-" -> {
                    tokenizer.consume()
                    result -= parseTerm(tokenizer)
                }
                else -> break // Not an addition/subtraction operator
            }
        }
        return result
    }

    // Parses multiplication and division (higher precedence than addition/subtraction)
    private fun parseTerm(tokenizer: Tokenizer): Double {
        var result = parseFactor(tokenizer)
        
        while (tokenizer.hasMoreTokens()) {
            val operator = tokenizer.peek()
            when (operator) {
                "*" -> {
                    tokenizer.consume()
                    result *= parseFactor(tokenizer)
                }
                "/" -> {
                    tokenizer.consume()
                    val divisor = parseFactor(tokenizer)
                    if (divisor == 0.0) {
                        throw ArithmeticException("Division by zero")
                    }
                    result /= divisor
                }
                else -> break // Not a multiplication/division operator
            }
        }
        return result
    }

    // Parses factors: numbers, parentheses, and unary negation (highest precedence)
    private fun parseFactor(tokenizer: Tokenizer): Double {
        val token = tokenizer.peek()
        
        return when {
            token == "(" -> {
                tokenizer.consume()
                val result = parseExpression(tokenizer)
                if (!tokenizer.consumeIfExpected(")")) {
                    throw IllegalArgumentException("Missing closing parenthesis")
                }
                result
            }
            token == "-" -> {
                tokenizer.consume()
                -parseFactor(tokenizer)
            }
            token.toDoubleOrNull() != null -> tokenizer.consume().toDouble()
            else -> throw IllegalArgumentException("Invalid token: $token")
        }
    }

    /**
     * Tokenizes the input expression string into individual tokens.
     */
    private class Tokenizer(private val input: String) {
        private var position = 0

        fun peek(): String {
            val savedPosition = position
            skipWhitespace()
            val token = if (hasMoreTokens()) currentChar.toString() else ""
            position = savedPosition
            return token
        }

        fun consume(): String {
            skipWhitespace()
            if (!hasMoreTokens()) return ""
            
            return when {
                currentChar.isDigit() || currentChar == '.' -> readNumber()
                currentChar in "+-*/()" -> input[position++].toString()
                else -> throw IllegalArgumentException("Unexpected character: ${currentChar}")
            }
        }

        fun consumeIfExpected(expected: String): Boolean {
            if (peek() == expected) {
                consume()
                return true
            }
            return false
        }

        fun hasMoreTokens(): Boolean {
            skipWhitespace()
            return position < input.length
        }

        private val currentChar: Char get() = input[position]

        private fun skipWhitespace() {
            while (position < input.length && input[position] == ' ') {
                position++
            }
        }

        private fun readNumber(): String {
            val start = position
            while (position < input.length && (input[position].isDigit() || input[position] == '.')) {
                position++
            }
            return input.substring(start, position)
        }
    }
}