package com.alessandro.grocerylist

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
        val recyclerView = findViewById<RecyclerView>(R.id.itemList)

        loadItems()
        adapter = ItemAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        ItemTouchHelper(SwipeCallback()).attachToRecyclerView(recyclerView)

        addButton.setOnClickListener {
            val text = input.text.toString().trim()
            if (text.isNotEmpty()) {
                items.add(GroceryItem(text))
                sortAndRefresh()
                saveItems()
                input.text.clear()
            }
        }

        exportButton.setOnClickListener {
            exportList()
        }
    }

    /** Unchecked items first (in their existing order), checked item
