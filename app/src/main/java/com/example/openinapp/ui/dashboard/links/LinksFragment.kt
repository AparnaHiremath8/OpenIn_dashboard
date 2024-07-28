package com.example.openinapp.ui.dashboard.links

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.openinapp.R
import com.example.openinapp.data.model.RecentLink
import com.example.openinapp.data.model.TopLink
import com.example.openinapp.databinding.FragmentLinksBinding
import com.example.openinapp.ui.dashboard.links.adapter.RecentLinksAdapter
import com.example.openinapp.ui.dashboard.links.adapter.TopLinksAdapter
import com.example.openinapp.util.DividerItemDecoration
import com.example.openinapp.util.DrawableUtils
import com.example.openinapp.util.Resource
import com.example.openinapp.util.getGreetingMessage
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class LinksFragment : Fragment(), RecentLinksAdapter.Callbacks, TopLinksAdapter.Callbacks {

    private lateinit var linksViewModel: LinksViewModel

    private var _binding: FragmentLinksBinding? = null
    private val binding get() = _binding!!

    private var recentLinksAdapter: RecentLinksAdapter? = null
    private var mainList: MutableList<RecentLink> = ArrayList()
    private val dummyList: MutableList<RecentLink> = ArrayList()

    private var topLinksAdapter: TopLinksAdapter? = null
    private var topLinksList: MutableList<TopLink> = ArrayList()
    private var dummyTopLinksList: MutableList<TopLink> = ArrayList()

    lateinit var lineList: ArrayList<Entry>
    lateinit var lineDataSet: LineDataSet
    lateinit var lineData: LineData

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentLinksBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        linksViewModel = ViewModelProvider(
            this,
            LinksViewModelProvider(requireActivity().application)
        )[LinksViewModel::class.java]
        linksViewModel.getDashboardApiData()

        bindView()
        bindObserver()
    }

    private fun bindObserver() {
        linksViewModel.dashboardResponseResult.observe(viewLifecycleOwner, Observer { response ->
            when (response) {
                is Resource.Success -> {
                    handleShimmer(false)
                    _binding?.layoutClicks?.tvMainClicksText?.text = response.data?.today_clicks.toString()
                    _binding?.layoutSource?.tvMainSourceText?.text = response.data?.top_source
                    _binding?.layoutLocation?.tvMainLocationText?.text = response.data?.top_location
                    mainList.clear()
                    dummyList.clear()
                    topLinksList.clear()
                    dummyTopLinksList.clear()
                    response.data?.data?.recent_links?.let {
                        mainList = it.toMutableList()
                        if (mainList.size >= 4) {
                            dummyList.addAll(mainList.subList(0, 4))
                        }
                        recentLinksAdapter?.notifyDataSetChanged()
                    }
                    response.data?.data?.top_links?.let {
                        topLinksList = it.toMutableList()
                        if (topLinksList.size >= 4) {
                            dummyTopLinksList.addAll(topLinksList.subList(0, 4))
                        }
                        topLinksAdapter?.notifyDataSetChanged()
                    }
                    setGraphPropertiesAndValue(response.data?.data?.overall_url_chart)
                }
                is Resource.Error -> {
                    handleShimmer(false)
                    Toast.makeText(requireContext(), "Error loading data. Please try again later!", Toast.LENGTH_LONG).show()
                }
                is Resource.Loading -> {
                    handleShimmer(true)
                }
            }
        })
    }

    private fun bindView() {
        _binding?.rvRecentLinks?.layoutManager = LinearLayoutManager(requireContext())
        _binding?.rvTopLinks?.layoutManager = LinearLayoutManager(requireContext())
        val dividerItemDecoration = DividerItemDecoration(requireContext())
        _binding?.rvRecentLinks?.addItemDecoration(dividerItemDecoration)
        _binding?.rvTopLinks?.addItemDecoration(dividerItemDecoration)

        topLinksAdapter = TopLinksAdapter(dummyTopLinksList)
        topLinksAdapter!!.setCallback(this)
        topLinksAdapter!!.setWithFooter(true)
        _binding?.rvTopLinks?.adapter = topLinksAdapter

        recentLinksAdapter = RecentLinksAdapter(dummyList)
        recentLinksAdapter!!.setCallback(this)
        recentLinksAdapter!!.setWithFooter(true)
        _binding?.rvRecentLinks?.adapter = recentLinksAdapter

        _binding?.toggleGroup?.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btnTopLinks -> {
                        _binding?.rvRecentLinks?.visibility = View.GONE
                        _binding?.rvTopLinks?.visibility = View.VISIBLE
                    }
                    R.id.btnRecentLinks -> {
                        _binding?.rvTopLinks?.visibility = View.GONE
                        _binding?.rvRecentLinks?.visibility = View.VISIBLE
                    }
                }
            }
        }

        _binding?.tvGreetings?.text = getGreetingMessage()
    }

    private fun setGraphPropertiesAndValue(urlChartResponse: Map<String, Int>?) {
        lineList = ArrayList() // Initialize the lineList

        if (urlChartResponse.isNullOrEmpty()) {
            binding.chart.clear()
            return
        }

        val dateFormat = SimpleDateFormat("HH:mm", Locale.US)
        val startDateString = urlChartResponse.keys.minOrNull()
        val endDateString = urlChartResponse.keys.maxOrNull()

        if (startDateString == null || endDateString == null) {
            binding.chart.clear()
            return
        }

        val startDate = dateFormat.parse(startDateString)
        val endDate = dateFormat.parse(endDateString)

        if (startDate == null || endDate == null) {
            binding.chart.clear()
            return
        }

        val calendar = Calendar.getInstance()
        val textFormat = SimpleDateFormat("HH:mm", Locale.US)
        binding.tvDuration.text = "${textFormat.format(startDate)} - ${textFormat.format(endDate)}"

        urlChartResponse.forEach { (key, value) ->
            val date = dateFormat.parse(key)
            if (date != null && date in startDate..endDate) {
                val daysSinceStart = TimeUnit.MILLISECONDS.toMinutes(date.time - startDate.time).toFloat()
                lineList.add(Entry(daysSinceStart, value.toFloat()))
            }
        }

        lineDataSet = LineDataSet(lineList, null).apply {
            color = Color.parseColor("#0E6FFF")
            setDrawValues(false)
            setDrawCircles(false)
            setDrawFilled(true)
            fillDrawable = DrawableUtils.createGradientDrawable(Color.parseColor("#0E6FFF"), Color.TRANSPARENT)
        }

        lineData = LineData(lineDataSet)
        binding.chart.apply {
            data = lineData
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                setDrawAxisLine(true)
                setDrawLabels(true)
                valueFormatter = object : ValueFormatter() {
                    private val format = SimpleDateFormat("HH:mm", Locale.US)
                    override fun getFormattedValue(value: Float): String {
                        calendar.time = startDate
                        calendar.add(Calendar.DAY_OF_YEAR, value.toInt())
                        return if (value.toInt() % 5 == 0) format.format(calendar.time) else ""
                    }
                }
                granularity = 1f
                labelCount = TimeUnit.MILLISECONDS.toMinutes(endDate.time - startDate.time).toInt()
            }
            axisRight.isEnabled = false
            description.isEnabled = false
            legend.isEnabled = false
            invalidate()
        }
    }

    private fun handleShimmer(isShimmering: Boolean) {
        if (isShimmering) {
            _binding?.shimmerFrameLayout?.root?.visibility = View.VISIBLE
            _binding?.mainLayout?.visibility = View.GONE
        } else {
            _binding?.shimmerFrameLayout?.root?.visibility = View.GONE
            _binding?.mainLayout?.visibility = View.VISIBLE
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onClickLoadMoreRecentLinks() {
        recentLinksAdapter!!.setWithFooter(false) // hide footer
        dummyList.addAll(mainList.subList(4, mainList.size))
        recentLinksAdapter!!.notifyDataSetChanged()
    }

    override fun onRecentLinksItemClicked(recentLink: RecentLink) {
        val clipboardManager = activity?.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = ClipData.newPlainText("text", recentLink.web_link)
        clipboardManager.setPrimaryClip(clipData)
    }

    override fun onClickLoadMoreTopLinks() {
        topLinksAdapter!!.setWithFooter(false)
        dummyTopLinksList.addAll(topLinksList.subList(4, topLinksList.size))
        topLinksAdapter!!.notifyDataSetChanged()
    }

    override fun onTopLinksItemClicked(topLink: TopLink) {
        val clipboardManager = activity?.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = ClipData.newPlainText("text", topLink.web_link)
        clipboardManager.setPrimaryClip(clipData)
    }
}
