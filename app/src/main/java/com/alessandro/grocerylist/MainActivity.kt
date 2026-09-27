package com.alessandro.grocerylist

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val items = mutableListOf<String>()
    private lateinit var adapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val input = findViewById<EditText>(R.id.itemInput)
        val addButton = findViewById<Button>(R.id.addButton)
        val listView = findViewById<ListView>(R.id.itemList)

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, items)
        listView.adapter = adapter

        addButton.setOnClickListener {
            val text = input.text.toString().trim()
            if (text.isNotEmpty()) {
                items.add(text)
                adapter.notifyDataSetChanged()
                input.text.clear()
            }
        }

        listView.setOnItemClickListener { _, _, position, _ ->
            items.removeAt(position)
            adapter.notifyDataSetChanged()
        }
    }
}
