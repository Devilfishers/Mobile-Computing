package com.example.myapplication
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.myapplication.databinding.ActivityMainBinding
import java.util.function.UnaryOperator

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var calculator: Calculator
    private lateinit var historyManager: HistoryManager
    private var currentExpression = ""
    private var lastResult = ""


    private val numberButtons by lazy{
        listOf(binding.zero, binding.one, binding.two, binding.three, binding.four,
            binding.five, binding.six, binding.seven, binding.eight, binding.nine)
    }
    private val operatorButtons by lazy{
        listOf(binding.plus, binding.substract, binding.multiply, binding.divide)
    }



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        enableEdgeToEdge()
        setContentView(binding.root)

        calculator = Calculator()
        historyManager = HistoryManager(this)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupClickListeners()
        updateHistoryDisplay()

    }

    private fun setupClickListeners(){
        // Number buttons
        numberButtons.forEachIndexed { index, button ->
            button.setOnClickListener { appendToExpression(index.toString()) }
        }
        // Decimal point
        binding.decimal.setOnClickListener { appendToExpression(".") }

        // Operators
        binding.plus.setOnClickListener { appendOperator("+") }
        binding.substract.setOnClickListener { appendOperator("-") }
        binding.multiply.setOnClickListener { appendOperator("×") }
        binding.divide.setOnClickListener { appendOperator("÷") }

        // Parentheses
        binding.leftpar.setOnClickListener { appendToExpression("(") }
        binding.rightpar.setOnClickListener { appendToExpression(")") }

        // Control buttons
        binding.clear.setOnClickListener { clearExpression() }
        binding.delete.setOnClickListener { deleteLastCharacter() }
        binding.equal.setOnClickListener { calculateResult() }

        // Save button
        binding.savebutton.setOnClickListener { saveToFile() }

        // Clear History button
        binding.clearHistoryButton.setOnClickListener {
            historyManager.clearHistory()
            updateHistoryDisplay()
            Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show()
        }

    }

    private fun appendToExpression(text: String){
        //Check if two zeros are klicked, if so, ignore
        if(text == "0" && currentExpression == "0") return
        //Check if one zero is cklicked followed by a digit, change the zero to the digit
        if( currentExpression == "0" && text.all{it. isDigit() }){
            currentExpression = text
        }else{
            currentExpression += text
        }
        updateDisplay()
    }

    private fun appendOperator(operator: String){
        //ignore if the expression is empty
        if (currentExpression.isEmpty()){
            if (operator == "-"){
                currentExpression = operator
            }
            return
        }
        val lastChar = currentExpression.last()
        //check if multiple operators are used, if so drop the last one
        if (lastChar in "+-x÷"){
            currentExpression = currentExpression.dropLast(1) + operator
        }else{
            currentExpression += operator
        }
        updateDisplay()
    }

    private fun clearExpression(){
        currentExpression = ""
        lastResult = ""
        binding.caclcView.text = ""
    }
    private fun deleteLastCharacter(){
        if (currentExpression.isNotEmpty()){
            currentExpression = currentExpression.dropLast(1)
            updateDisplay()
        }
    }
    private fun calculateResult(){
        if (currentExpression.isEmpty()) return
        val result = calculator.evaluate(currentExpression)
        when (result) {
            "DIV_ZERO" -> {
                Toast.makeText(this, "Cannot divide by zero", Toast.LENGTH_SHORT).show()
                currentExpression = ""
            }
            "INVALID" -> {
                Toast.makeText(this, "Invalid expression", Toast.LENGTH_SHORT).show()
                currentExpression = ""
            }
            "" -> return
            else -> {
                historyManager.saveToHistory(currentExpression, result)
                updateHistoryDisplay()
                lastResult = currentExpression
                currentExpression = result
            }
        }
        updateDisplay()
    }
    private fun updateDisplay(){
        binding.caclcView.text = currentExpression
    }
    
    private fun updateHistoryDisplay(){
        binding.historyView.text = historyManager.getLastEntries(5)
    }
    private fun saveToFile(){
        if (historyManager.exportToDownloads()){
            Toast.makeText(this, "History saved to Files", Toast.LENGTH_SHORT).show()
        }else{
            Toast.makeText(this, "Failed to save", Toast.LENGTH_SHORT).show()
        }
    }
}