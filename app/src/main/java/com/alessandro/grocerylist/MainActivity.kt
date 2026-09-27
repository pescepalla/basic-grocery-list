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

    private fun applyCheckedStyle(textView: TextView, checked: Boolean) {
        val colorRes = if (checked) R.color.item_text_checked else R.color.item_text
        textView.setTextColor(ContextCompat.getColor(textView.context, colorRes))
        if (checked) {
            textView.paintFlags = textView.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        } else {
            textView.paintFlags = textView.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        }
    }

    private inner class ItemAdapter : RecyclerView.Adapter<ItemAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val checkBox: CheckBox = view.findViewById(R.id.itemCheck)
            val textView: TextView = view.findViewById(R.id.itemText)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.list_item, parent, false)
            return ViewHolder(view)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]

            // Avoid firing the listener while we set the checkbox state on bind.
            holder.checkBox.setOnCheckedChangeListener(null)
            holder.checkBox.isChecked = item.checked
            holder.textView.text = item.name
            applyCheckedStyle(holder.textView, item.checked)

            holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
                item.checked = isChecked
                sortAndRefresh()
                saveItems()
            }
        }
    }

    /** Swipe right to toggle checked, swipe left to delete. */
    private inner class SwipeCallback :
        ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean = false

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            val position = viewHolder.bindingAdapterPosition
            if (position == RecyclerView.NO_POSITION) return

            when (direction) {
                ItemTouchHelper.LEFT -> {
                    items.removeAt(position)
                    adapter.notifyItemRemoved(position)
                    saveItems()
                }
                ItemTouchHelper.RIGHT -> {
                    items[position].checked = !items[position].checked
                    sortAndRefresh()
                    saveItems()
                }
            }
        }

        override fun onChildDraw(
            c: Canvas,
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            dX: Float,
            dY: Float,
            actionState: Int,
            isCurrentlyActive: Boolean
        ) {
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)

            val itemView = viewHolder.itemView
            val radius = 12 * itemView.resources.displayMetrics.density
            val paint = Paint()

            val rect = when {
                dX > 0 -> {
                    paint.color = ContextCompat.getColor(itemView.context, R.color.swipe_check_bg)
                    RectF(itemView.left.toFloat(), itemView.top.toFloat(), itemView.left + dX, itemView.bottom.toFloat())
                }
                dX < 0 -> {
                    paint.color = ContextCompat.getColor(itemView.context, R.color.swipe_delete_bg)
                    RectF(itemView.right + dX, itemView.top.toFloat(), itemView.right.toFloat(), itemView.bottom.toFloat())
                }
                else -> return
            }
            c.drawRoundRect(rect, radius, radius, paint)
        }
    }
}
