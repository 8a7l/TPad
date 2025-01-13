package com.example.tpad

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.IOException
import android.app.AlertDialog
import android.text.Html
import android.text.method.LinkMovementMethod
import android.widget.TextView
import android.text.Spannable
import android.text.style.ForegroundColorSpan
import android.graphics.Color
import android.content.ContextWrapper
import android.util.Log // Імпортуємо Log

class MainActivity : AppCompatActivity() {
    private lateinit var editText: EditText
    private lateinit var menuButton: Button

    private var currentFileUri: Uri? = null
    private var lastSearchIndex = -1 // Зберігає індекс останнього знайденого входження

    private lateinit var openFileLauncher: ActivityResultLauncher<Intent>
    private lateinit var saveFileAsLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        editText = findViewById(R.id.editText)
        menuButton = findViewById(R.id.menuButton)

        // Реєстрація лончерів для дій
        openFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let { uri ->
                    currentFileUri = uri
                    loadFile(uri)
                }
            }
        }

        saveFileAsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let { uri ->
                    currentFileUri = uri
                    try {
                        contentResolver.openOutputStream(uri)?.use { outputStream ->
                            // Записуємо текст в обраний файл
                            val content = editText.text.toString()
                            outputStream.write(content.toByteArray())
                            Toast.makeText(this, "Файл збережено як ${uri.path}", Toast.LENGTH_SHORT).show()
                            Log.d("SaveFileAs", "File saved: $uri")
                        }
                    } catch (e: IOException) {
                        Toast.makeText(this, "Помилка при збереженні: ${e.message}", Toast.LENGTH_SHORT).show()
                        Log.e("SaveFileAs", "Error saving file: ${e.message}")
                    }
                }
            }
        }

        menuButton.setOnClickListener { view ->
            showPopupMenu(view)
        }
    }

    private fun showPopupMenu(view: android.view.View) {
        val popupMenu = PopupMenu(this, view)

        // Завантажуємо меню
        popupMenu.menuInflater.inflate(R.menu.file_menu, popupMenu.menu)

        // Тут ви встановлюєте стиль для меню
        val menu = popupMenu.menu
        for (i in 0 until menu.size()) {
            val item = menu.getItem(i)
            // Використовуємо метод для зміни кольору (можна змінити за допомогою стилю або інших методів)
            item.icon?.setTint(Color.BLACK)  // Налаштування кольору іконок (якщо є)
            item.setTitle(Html.fromHtml("<font color='#808080'>" + item.title + "</font>", Html.FROM_HTML_MODE_LEGACY)) // Налаштування кольору тексту
        }

        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_new -> {
                    createNewFile()
                    true
                }
                R.id.menu_open -> {
                    openFile()
                    true
                }
                R.id.menu_save -> {
                    saveToFile()
                    true
                }
                R.id.menu_save_as -> {
                    saveFileAs()
                    true
                }
                R.id.menu_close -> {
                    closeFile()
                    true
                }
                R.id.menu_about -> {
                    showAboutDialog() // Виклик діалогового вікна "Про програму"
                    true
                }
				R.id.menu_exit -> {
					finishAffinity() // Закриває всі Activity і завершує роботу додатку
					true
				}
                R.id.menu_search -> {
                    showSearchDialog() // Додано пункт пошуку
                    true
                }
                else -> false
            }
        }

        popupMenu.show()
    }

    private fun saveToFile() {
        currentFileUri?.let { uri ->
            try {
                contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(editText.text.toString().toByteArray())
                    Toast.makeText(this, "Файл збережено", Toast.LENGTH_SHORT).show()
                }
            } catch (e: IOException) {
                Toast.makeText(this, "Помилка при збереженні", Toast.LENGTH_SHORT).show()
            }
        } ?: Toast.makeText(this, "Спочатку відкрийте або збережіть файл", Toast.LENGTH_SHORT).show()
    }

    private fun saveFileAs() {
        // Створюємо інтерфейс для вибору формату файлу
        val formats = arrayOf(
            "text/plain" to "txt", // Звичайний текст
            "text/html" to "html", // HTML
            "application/x-php" to "php", // PHP
            "text/css" to "css", // CSS
            "application/javascript" to "js", // JavaScript
            "application/xml" to "xml", // XML
            "text/csv" to "csv", // CSV
            "application/json" to "json" // JSON
        )

        // Створюємо діалог для вибору формату файлу
        val formatDialog = AlertDialog.Builder(this)
            .setTitle("Виберіть формат файлу")
            .setItems(formats.map { it.second }.toTypedArray()) { _, which ->
                val selectedFormat = formats[which]
                val mimeType = selectedFormat.first
                val extension = selectedFormat.second

                // Створюємо діалог для введення імені файлу
                val input = EditText(this).apply {
                    hint = "Введіть ім'я файлу"
                }

                AlertDialog.Builder(this)
                    .setTitle("Ім'я файлу")
                    .setView(input)
                    .setPositiveButton("Зберегти") { _, _ ->
                        val fileName = input.text.toString().trim()

                        // Перевірка, чи не порожнє ім'я файлу
                        if (fileName.isEmpty()) {
                            Toast.makeText(this, "Ім'я файлу не може бути порожнім", Toast.LENGTH_SHORT).show()
                            return@setPositiveButton
                        }

                        // Якщо користувач не вибрав розширення, зберігаємо без нього
                        val finalFileName = if (fileName.contains(".")) fileName else "$fileName.$extension"

                        // Створюємо інтенцію для збереження файлу з вибраним форматом
                        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                            type = mimeType
                            putExtra(Intent.EXTRA_TITLE, finalFileName) // Встановлюємо ім'я файлу з розширенням або без нього
                        }

                        // Запускаємо лончер для збереження файлу
                        saveFileAsLauncher.launch(intent)
                    }
                    .setNegativeButton("Скасувати", null)
                    .create()
                    .show()
            }
            .setNegativeButton("Скасувати", null)
            .create()

        formatDialog.show()
    }

    private fun openFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "*/*" // Відкриваємо всі текстові файли
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        openFileLauncher.launch(intent)
    }

    private fun closeFile() {
        editText.text.clear()
        currentFileUri = null
        Toast.makeText(this, "Файл закрито", Toast.LENGTH_SHORT).show()
    }

    private fun createNewFile() {
        editText.text.clear()
        currentFileUri = null
        Toast.makeText(this, "Новий документ створено", Toast.LENGTH_SHORT).show()
    }

    private fun loadFile(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val text = inputStream.bufferedReader().use { it.readText() }
                editText.setText(text)
            }
        } catch (e: IOException) {
            // Виводимо повідомлення про помилку, але не закриваємо програму
            Toast.makeText(this, "Помилка при відкритті файлу: ${e.message}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            // Обробка інших можливих помилок (наприклад, не текстовий файл)
            Toast.makeText(this, "Невідповідний формат файлу або інша помилка: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAboutDialog() {
        val htmlText = """
			<h2>Автор:</h2>
            <p>Василь Онуфрійчук</p>
            <a href='https://github.com/8a7l/TPad'>github TPad</a></p>
			<h2>Про програму</h2>
            <p>TPad - <b>простий текстовий редактор</b>.</p>
			<p>Ця програма є безкоштовною, ви можете поширювати її та/або змінювати гідно з умовами GNU General Public License версії 3, опублікованої Free Software Foundation; або на ваш вибір будь-якої пізнішої версії.</p>
			</b>
			<p>Ця програма розповсюджується в надії, що вона буде корисною, але БЕЗ ЖОДНИХ ГАРАНТІЙ; навіть без неявної гарантії ПРИДАТНОСТІ ДЛЯ ПРОДАЖУ чи ВІДПОВІДНОСТІ ДЛЯ КОНКРЕТНОЇ МЕТИ.</p>
            <p>Ознайомитись з ліцензією можна за посиланям:<br>
            <a href='https://www.gnu.org/licenses/lgpl-3.0.html'>GNU General Public License версії 3</a></p>
			<br>
			
        """.trimIndent()

        val dialog = AlertDialog.Builder(this)
            .setTitle("Про програму")
            .setMessage(Html.fromHtml(htmlText, Html.FROM_HTML_MODE_LEGACY)) // Використовуємо новий метод
			.setPositiveButton("Закрити") { _, _ ->
				// Оскільки dialog не використовується, замінюємо його на _
			}

            .create()

        dialog.show()

        // Додає можливість натискання на посилання
        (dialog.findViewById(android.R.id.message) as? TextView)?.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun showSearchDialog() {
        val inputEditText = EditText(this)
        inputEditText.hint = "Введіть текст для пошуку"

        AlertDialog.Builder(this)
            .setTitle("Пошук")
            .setView(inputEditText)
            .setPositiveButton("Шукати") { _, _ ->
                val searchQuery = inputEditText.text.toString()
                if (searchQuery.isNotEmpty()) {
                    performSearch(searchQuery)
                } else {
                    Toast.makeText(this, "Будь ласка, введіть текст для пошуку", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Скасувати", null)
            .show()
    }

    private fun performSearch(query: String) {
        val content = editText.text.toString()

        // Пошук запиту в текстовому вмісті
        val index = content.indexOf(query, lastSearchIndex + 1)
        if (index != -1) {
            // Виділяє знайдений текст
            lastSearchIndex = index
            val spannable = Spannable.Factory.getInstance().newSpannable(content)
            spannable.setSpan(ForegroundColorSpan(Color.RED), index, index + query.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            editText.setText(spannable)

            // Прокручування до знайденого тексту
            editText.setSelection(index)
        } else {
            Toast.makeText(this, "Текст не знайдено", Toast.LENGTH_SHORT).show()
        }
    }
}
