package com.hereliesaz.barcodencrypt.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hereliesaz.barcodencrypt.data.Contact
import com.hereliesaz.barcodencrypt.data.ContactRepository
import com.hereliesaz.barcodencrypt.data.ContactWithBarcodes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ContactViewModel @Inject constructor(
    private val repository: ContactRepository
) : ViewModel() {

    val allContacts: LiveData<List<ContactWithBarcodes>> = repository.allContacts
    val serviceStatus = MutableLiveData<Boolean>()
    val notificationPermissionStatus = MutableLiveData<Boolean>()

    fun insertContact(contact: Contact) = viewModelScope.launch(Dispatchers.IO) {
        repository.insertContact(contact)
    }
}
