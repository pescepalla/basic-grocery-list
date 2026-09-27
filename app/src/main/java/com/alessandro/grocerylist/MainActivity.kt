package com.alessandro.grocerylist

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import android.widget.ImageButton
import androidx.activity.result.contract.ActivityResultContracts
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
    private lateinit var itemInput: EditText

    companion object {
        private const val PREFS_NAME = "grocery_prefs"
        private const val KEY_ITEMS = "items"
    }

    private val createBackupLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            uri?.let { writeBackup(it) }
        }

    private val openBackupLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { readBackup(it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        itemInput = findViewById(R.id.itemInput)
        val addButton = findViewById<Button>(R.id.addButton)
        val shareButton = findViewById<ImageButton>(R.id.shareButton)
        val backupButton = findViewById<ImageButton>(R.id.backupButton)
        val restoreButton = findViewById<ImageButton>(R.id.restoreButton)
        val recyclerView = findViewById<RecyclerView>(R.id.itemList)


        loadItems()
        adapter = ItemAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        ItemTouchHelper(SwipeCallback()).attachToRecyclerView(recyclerView)

        addButton.setOnClickListener {
            val text = itemInput.text.toString().trim()
            if (text.isNotEmpty()) {
                items.add(GroceryItem(text))
                sortAndRefresh()
                saveItems()
                itemInput.text.clear()
            }
        }

        shareButton.setOnClickListener {
            shareList()
        }

        backupButton.setOnClickListener {
            createBackupLauncher.launch("grocery-list-backup.json")
        }

        restoreButton.setOnClickListener {
            openBackupLauncher.launch(arrayOf("application/json", "text/plain"))
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

    private fun itemsToJson(): String {
        val array = JSONArray()
        for (item in items) {
            val obj = JSONObject()
            obj.put("name", item.name)
            obj.put("checked", item.checked)
            array.put(obj)
        }
        return array.toString()
    }

    private fun parseItemsJson(json: String): List<GroceryItem> {
        val array = JSONArray(json)
        val result = mutableListOf<GroceryItem>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            result.add(GroceryItem(obj.getString("name"), obj.getBoolean("checked")))
        }
        return result
    }

    private fun saveItems() {
        prefs.edit().putString(KEY_ITEMS, itemsToJson()).apply()
    }

    private fun loadItems() {
        val json = prefs.getString(KEY_ITEMS, null) ?: return
        items.addAll(parseItemsJson(json))
    }

    /** Shares only what's still needed (unchecked items) as plain text, e.g. with a partner. */
    private fun shareList() {
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
        startActivity(Intent.createChooser(sendIntent, "Share list via"))
    }

    /** Saves the full list (including checked state) to a JSON file the user picks. */
    private fun writeBackup(uri: Uri) {
        try {
            contentResolver.openOutputStream(uri)?.use { out ->
                out.write(itemsToJson().toByteArray())
            }
            Toast.makeText(this, "Backup saved", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Backup failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /** Replaces the current list with the contents of a previously saved backup file. */
    private fun readBackup(uri: Uri) {
        try {
            val json = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            if (json == null) {
                Toast.makeText(this, "Could not read file", Toast.LENGTH_SHORT).show()
                return
            }
            val restored = parseItemsJson(json)
            items.clear()
            items.addAll(restored)
            sortAndRefresh()
            saveItems()
            Toast.makeText(this, "Restored ${restored.size} items", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Restore failed: invalid backup file", Toast.LENGTH_SHORT).show()
        }
    }

    /** Pulls an item's text back into the top input field so the user can edit and re-add it. */
    private fun editItem(item: GroceryItem) {
        itemInput.setText(item.name)
        itemInput.setSelection(itemInput.text.length)
        itemInput.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(itemInput, InputMethodManager.SHOW_IMPLICIT)
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

    /** Swipe right to edit (moves the item back into the input field), swipe left to delete. */
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
                    val item = items.removeAt(position)
                    adapter.notifyItemRemoved(position)
                    saveItems()
                    editItem(item)
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
            val density = itemView.resources.displayMetrics.density
            val radius = 12 * density
            val iconSize = (24 * density).toInt()
            val iconMargin = (16 * density).toInt()
            val paint = Paint()

            when {
                dX > 0 -> {
                    paint.color = ContextCompat.getColor(itemView.context, R.color.swipe_edit_bg)
                    val rect = RectF(itemView.left.toFloat(), itemView.top.toFloat(), itemView.left + dX, itemView.bottom.toFloat())
                    c.drawRoundRect(rect, radius, radius, paint)

                    if (dX > iconSize + iconMargin * 2) {
                        val icon = ContextCompat.getDrawable(itemView.context, R.drawable.ic_edit_white)
                        icon?.let {
                            val top = itemView.top + (itemView.height - iconSize) / 2
                            val left = itemView.left + iconMargin
                            it.setBounds(left, top, left + iconSize, top + iconSize)
                            it.draw(c)
                        }
                    }
                }
                dX < 0 -> {
                    paint.color = ContextCompat.getColor(itemView.context, R.color.swipe_delete_bg)
                    val rect = RectF(itemView.right + dX, itemView.top.toFloat(), itemView.right.toFloat(), itemView.bottom.toFloat())
                    c.drawRoundRect(rect, radius, radius, paint)

                    if (-dX > iconSize + iconMargin * 2) {
                        val icon = ContextCompat.getDrawable(itemView.context, R.drawable.ic_delete_white)
                        icon?.let {
                            val top = itemView.top + (itemView.height - iconSize) / 2
                            val right = itemView.right - iconMargin
                            it.setBounds(right - iconSize, top, right, top + iconSize)
                            it.draw(c)
                        }
                    }
                }
                else -> return
            }
        }
    }
}
