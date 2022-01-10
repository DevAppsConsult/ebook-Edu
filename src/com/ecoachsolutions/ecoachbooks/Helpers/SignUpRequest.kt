package com.ecoachsolutions.ecoachbooks.Helpers


class SignUpRequest {
    var Email: String = "";
    var Password: String = "";
    var ConfirmPassword: String = "";
    var Phone: String = "";
    var Username: String = "";
    var FirstName: String = "";
    var LastName: String = "";
    var Country: String = "";
    var School: String = "";
}


data class SignInRequest(val email:String = "", val password:String = "")