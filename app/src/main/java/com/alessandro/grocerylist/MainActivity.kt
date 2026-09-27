package com.alessandro.grocerylist

import android.content.Intent
import android.content.SharedPreferences
import androidx.core.content.ContextCompat
import android.graphics.Paint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject

data class GroceryItem(var name: String, var checked: Boolean = false)

class MainActivity : AppCompatActivity() {

    private val items = mutableListOf<GroceryItem>()
    private lateinit var adapter: ItemAdapter
    private lateinit var prefs: SharedPreferences

    companion object {
        private const val PREFS_NAME = "grocery_prefs"
        private const val KEY_ITEMS = "items"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        val input = findViewById<EditText>(R.id.itemInput)
        val addButton = findViewById<Button>(R.id.addButton)
        val exportButton = findViewById<Button>(R.id.exportButton)
        val listView = findViewById<ListView>(R.id.itemList)

        loadItems()
        adapter = ItemAdapter()
        listView.adapter = adapter

        addButton.setOnClickListener {
            val text = input.text.toString().trim()
            if (text.isNotEmpty()) {
                items.add(GroceryItem(text))
                sortAndRefresh()
                saveItems()
                input.text.clear()
            }
        }

        listView.setOnItemLongClickListener { _, _, position, _ ->
            items.removeAt(position)
            sortAndRefresh()
            saveItems()
            true
        }

        exportButton.setOnClickListener {
            exportList()
        }
    }

    /** Unchecked items first (in their existing order), checked items after. */
    private fun sortAndRefresh() {
        val unchecked = items.filter { !it.checked }
        val checked = items.filter { it.checked }
        items.clear()
        items.addAll(unchecked)
        items.addAll(checked)
        adapter.notifyDataSetChanged()
    }

    private fun saveItems() {
        val array = JSONArray()
        for (item in items) {
            val obj = JSONObject()
            obj.put("name", item.name)
            obj.put("checked", item.checked)
            array.put(obj)
        }
        prefs.edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    private fun loadItems() {
        val json = prefs.getString(KEY_ITEMS, null) ?: return
        val array = JSONArray(json)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            items.add(GroceryItem(obj.getString("name"), obj.getBoolean("checked")))
        }
    }

    private fun exportList() {
        val needed = items.filter { !it.checked }
        if (needed.isEmpty()) {
            Toast.makeText(this, "Nothing left to buy", Toast.LENGTH_SHORT).show()
            return
        }
        val text = needed.joinToString("\n") { it.name }
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Grocery List")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(sendIntent, "Export list via"))
    }

    private inner class ItemAdapter : BaseAdapter() {
        override fun getCount(): Int = items.size
        override fun getItem(position: Int): GroceryItem = items[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(this@MainActivity)
                .inflate(R.layout.list_item, parent, false)

            val item = items[position]
            val checkBox = view.findViewById<CheckBox>(R.id.itemCheck)
            val textView = view.findViewById<TextView>(R.id.itemText)

            // Avoid firing the listener while we set the checkbox state on bind.
            checkBox.setOnCheckedChangeListener(null)
            checkBox.isChecked = item.checked
            textView.text = item.name
            applyCheckedStyle(textView, item.checked)

            checkBox.setOnCheckedChangeListener { _, isChecked ->
                item.checked = isChecked
                sortAndRefresh()
                saveItems()
            }

            return view
        }

        private fun applyCheckedStyle(textView: TextView, checked: Boolean) {
            if (checked) {
                textView.paintFlags = textView.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                textView.setTextColor(Color.GRAY)
            } else {
                textView.paintFlags = textView.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                textView.setTextColor(Color.BLACK)
            }
        }
    }
}
