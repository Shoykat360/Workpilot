package com.example.workpilotmini.ui.visit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.workpilotmini.localization.Strings
import com.example.workpilotmini.ui.theme.AccentBlue
import com.example.workpilotmini.ui.theme.AccentBlueBg
import com.example.workpilotmini.ui.theme.AccentGreen
import com.example.workpilotmini.ui.theme.AccentGreenBg
import com.example.workpilotmini.ui.theme.AccentOrange
import com.example.workpilotmini.ui.theme.AccentOrangeBg
import com.example.workpilotmini.ui.theme.AccentViolet
import com.example.workpilotmini.ui.theme.AccentVioletBg
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisitEntryScreen(
    viewModel: VisitViewModel,
    ownerId: String,
    isSolo: Boolean,
    uid: String,
    userName: String,
    onSaved: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    var leadName by remember { mutableStateOf("") }
    var businessType by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var contactPerson by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var visitPurpose by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var nextVisitMillis by remember { mutableStateOf<Long?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val coordFormat = remember { java.text.DecimalFormat("0.00000") }

    LaunchedEffect(Unit) { viewModel.captureLocation(context) }

    LaunchedEffect(state.saved) {
        if (state.saved) {
            leadName = ""; businessType = ""; address = ""
            contactPerson = ""; phoneNumber = ""; visitPurpose = ""; notes = ""
            nextVisitMillis = null
            viewModel.resetSaved()
            onSaved()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // ---- Header ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(AccentBlueBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = AccentBlue)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    Strings.visitEntryHeader(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    Strings.visitEntrySubtitle(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        RequiredFieldCard(
            icon = Icons.Default.Person,
            iconTint = AccentBlue,
            iconBg = AccentBlueBg,
            label = Strings.leadName(),
            required = true
        ) {
            OutlinedTextField(
                value = leadName,
                onValueChange = { leadName = it },
                placeholder = { Text(Strings.leadNamePlaceholder()) },
                singleLine = true,
                trailingIcon = { Icon(Icons.Default.Storefront, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
        }

        Spacer(Modifier.height(14.dp))

        RequiredFieldCard(
            icon = Icons.Default.Business,
            iconTint = AccentViolet,
            iconBg = AccentVioletBg,
            label = Strings.businessType(),
            required = true
        ) {
            OutlinedTextField(
                value = businessType,
                onValueChange = { businessType = it },
                placeholder = { Text(Strings.businessTypePlaceholder()) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
        }

        Spacer(Modifier.height(14.dp))

        RequiredFieldCard(
            icon = Icons.Default.PinDrop,
            iconTint = AccentOrange,
            iconBg = AccentOrangeBg,
            label = Strings.visitLocationLabel(),
            required = true
        ) {
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                placeholder = { Text(Strings.visitLocationPlaceholder()) },
                singleLine = true,
                trailingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
        }

        Spacer(Modifier.height(14.dp))

        RequiredFieldCard(
            icon = Icons.Default.Person,
            iconTint = AccentBlue,
            iconBg = AccentBlueBg,
            label = Strings.contactPerson()
        ) {
            OutlinedTextField(
                value = contactPerson,
                onValueChange = { contactPerson = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
        }

        Spacer(Modifier.height(14.dp))

        RequiredFieldCard(
            icon = Icons.Default.Phone,
            iconTint = AccentGreen,
            iconBg = AccentGreenBg,
            label = Strings.phoneNumber()
        ) {
            OutlinedTextField(
                value = phoneNumber,
                onValueChange = { phoneNumber = it },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone
                ),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
        }

        Spacer(Modifier.height(14.dp))

        RequiredFieldCard(
            icon = Icons.Default.Business,
            iconTint = AccentViolet,
            iconBg = AccentVioletBg,
            label = Strings.visitPurpose()
        ) {
            OutlinedTextField(
                value = visitPurpose,
                onValueChange = { visitPurpose = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
        }

        Spacer(Modifier.height(14.dp))

        RequiredFieldCard(
            icon = Icons.Default.EditNote,
            iconTint = AccentBlue,
            iconBg = AccentBlueBg,
            label = Strings.notes()
        ) {
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                placeholder = { Text(Strings.notesPlaceholder()) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
        }

        Spacer(Modifier.height(16.dp))

        // ---- GPS location status card ----
        val (gpsBg, gpsBorder) = when {
            state.capturedLat != null && state.capturedLng != null -> AccentGreenBg to AccentGreen
            state.locationError != null -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.outlineVariant
        }
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = gpsBg,
            border = BorderStroke(1.dp, gpsBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (state.locationError != null) MaterialTheme.colorScheme.error.copy(alpha = 0.15f) else AccentGreen.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (state.isLocating) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            if (state.locationError != null) Icons.Default.Close else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (state.locationError != null) MaterialTheme.colorScheme.error else AccentGreen
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(Strings.currentLocationLabel(), style = MaterialTheme.typography.labelMedium)
                    when {
                        state.isLocating -> Text(Strings.locating(), style = MaterialTheme.typography.bodyMedium)
                        state.capturedLat != null && state.capturedLng != null -> Text(
                            "${coordFormat.format(state.capturedLat)}, ${coordFormat.format(state.capturedLng)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = AccentGreen
                        )
                        state.locationError != null -> Text(
                            state.locationError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        else -> Text(Strings.locationNotFound(), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (!state.isLocating) {
                    if (state.capturedLat != null && state.capturedLng != null) {
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = { Text(Strings.locationCaptured(), fontSize = 11.sp) },
                            colors = AssistChipDefaults.assistChipColors(
                                disabledLabelColor = AccentGreen,
                                disabledContainerColor = Color.Transparent
                            )
                        )
                    } else {
                        TextButton(onClick = { viewModel.captureLocation(context) }) { Text(Strings.retry()) }
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(Strings.nextVisitOptional(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = { showDatePicker = true },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(nextVisitMillis?.let { dateFormat.format(Date(it)) } ?: Strings.pickDate())
            }
            if (nextVisitMillis != null) {
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { nextVisitMillis = null }) { Text(Strings.clear()) }
            }
        }
        if (nextVisitMillis != null) {
            Spacer(Modifier.height(4.dp))
            Text(Strings.reminderHint(), style = MaterialTheme.typography.bodySmall)
        }

        state.errorMessage?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                viewModel.addVisit(
                    context, ownerId, isSolo, uid, userName,
                    leadName, businessType, address,
                    contactPerson, phoneNumber, visitPurpose, notes,
                    nextVisitMillis
                )
            },
            enabled = !state.isLoading,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(Strings.saveVisit(), fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(12.dp))
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = nextVisitMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    nextVisitMillis = pickerState.selectedDateMillis
                    showDatePicker = false
                }) { Text(Strings.ok()) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(Strings.cancel()) }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/**
 * A field wrapped in a light card: small colored icon + label (with a red "*" when
 * [required]) on top, the actual input [content] below. Mirrors the "Customer / দোকানের
 * নাম *" style blocks in the redesigned mockup.
 */
@Composable
private fun RequiredFieldCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    iconBg: Color,
    label: String,
    required: Boolean = false,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(15.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    buildAnnotatedRequiredLabel(label, required),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun buildAnnotatedRequiredLabel(label: String, required: Boolean) =
    androidx.compose.ui.text.buildAnnotatedString {
        append(label)
        if (required) {
            withStyle(androidx.compose.ui.text.SpanStyle(color = MaterialTheme.colorScheme.error)) {
                append(" *")
            }
        }
    }
















/*
package com.example.workpilotmini.ui.visit

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.workpilotmini.localization.Strings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisitEntryScreen(
    viewModel: VisitViewModel,
    ownerId: String,
    isSolo: Boolean,
    uid: String,
    userName: String,
    onSaved: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    var leadName by remember { mutableStateOf("") }
    var contactPerson by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var visitPurpose by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var nextVisitMillis by remember { mutableStateOf<Long?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
    val coordFormat = remember { java.text.DecimalFormat("0.00000") }

    LaunchedEffect(Unit) { viewModel.captureLocation(context) }

    LaunchedEffect(state.saved) {
        if (state.saved) {
            leadName = ""; contactPerson = ""; phoneNumber = ""; visitPurpose = ""; notes = ""
            nextVisitMillis = null
            viewModel.resetSaved()
            onSaved()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(Strings.visitEntryHeader(), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = leadName,
            onValueChange = { leadName = it },
            label = { Text(Strings.leadName()) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = contactPerson,
            onValueChange = { contactPerson = it },
            label = { Text(Strings.contactPerson()) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = phoneNumber,
            onValueChange = { phoneNumber = it },
            label = { Text(Strings.phoneNumber()) },
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = visitPurpose,
            onValueChange = { visitPurpose = it },
            label = { Text(Strings.visitPurpose()) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text(Strings.notes()) },
            minLines = 3,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(Strings.currentLocationLabel(), style = MaterialTheme.typography.labelMedium)
                    when {
                        state.isLocating -> Text(
                            Strings.locating(),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        state.capturedLat != null && state.capturedLng != null -> Text(
                            "${coordFormat.format(state.capturedLat)}, ${coordFormat.format(state.capturedLng)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        state.locationError != null -> Text(
                            state.locationError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        else -> Text(
                            Strings.locationNotFound(),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                if (state.isLocating) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    TextButton(onClick = { viewModel.captureLocation(context) }) { Text(Strings.retry()) }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(Strings.nextVisitOptional(), style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { showDatePicker = true }) {
                Text(nextVisitMillis?.let { dateFormat.format(Date(it)) } ?: Strings.pickDate())
            }
            if (nextVisitMillis != null) {
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { nextVisitMillis = null }) { Text(Strings.clear()) }
            }
        }
        if (nextVisitMillis != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                Strings.reminderHint(),
                style = MaterialTheme.typography.bodySmall
            )
        }

        state.errorMessage?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                viewModel.addVisit(
                    context, ownerId, isSolo, uid, userName,
                    leadName, contactPerson, phoneNumber, visitPurpose, notes,
                    nextVisitMillis
                )
            },
            enabled = !state.isLoading,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text(Strings.saveVisit())
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = nextVisitMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    nextVisitMillis = pickerState.selectedDateMillis
                    showDatePicker = false
                }) { Text(Strings.ok()) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(Strings.cancel()) }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}
*/
