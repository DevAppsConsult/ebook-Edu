package com.ecoachsolutions.ecoachbooks

import android.accounts.Account
import android.accounts.AccountAuthenticatorActivity
import android.accounts.AccountManager
import android.app.ProgressDialog
import android.content.Intent
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.*
import com.ecoachsolutions.ecoachbooks.Core.Database.EcoachBooksDatabase
import com.ecoachsolutions.ecoachbooks.Core.SessionManager
import com.ecoachsolutions.ecoachbooks.Helpers.*
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers

class LoginActivity : AccountAuthenticatorActivity() {
    // Declare variables
    internal var username: EditText? = null
    internal var password: EditText? = null
    internal var password2: EditText? = null
    internal var email: EditText? = null
    internal var phone: EditText? = null
    internal var country: EditText? = null
    internal var school: EditText? = null
    internal var fname: EditText? = null
    internal var lname: EditText? = null
    internal var viewSwitcher: ViewSwitcher? = null

    internal var doActionButton: Button? = null
    internal var toggleActionButton: Button? = null
    internal var signUpNextButton: Button? = null
    internal var signUpPrevButton: Button? = null
    internal var _apiSubscription: Subscription? = null
    internal var toggleActionPromptText: TextView? = null

    internal val _progressDialog: ProgressDialog by lazy { ProgressDialog(this); }
    internal val _accountManager: AccountManager by lazy { AccountManager.get(this) }
    internal val _ecoachBooksApi by lazy { EcoachBooksApi.getInstance(this) }
    internal var _isSignUp = false
    //animations
    internal val _slideInLeft by lazy { AnimationUtils.loadAnimation(this, R.anim.push_left_in) }
    internal val _slideOutright by lazy { AnimationUtils.loadAnimation(this, R.anim.push_right_out) }
    private val _preferences by lazy { EcoachBooksPreferences(this); }
    private val _activityContext by lazy { this }

    internal val _logger = LogHelper(this);

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // set content view to be login.xml
        setContentView(R.layout.login2)

        username = findViewById(R.id.userName) as EditText

        password = findViewById(R.id.userPass) as EditText
        password2 = findViewById(R.id.userPass2) as EditText
        email = findViewById(R.id.email) as EditText
        phone = findViewById(R.id.phone) as EditText
        country = findViewById(R.id.country) as EditText
        school = findViewById(R.id.school) as EditText
        fname = findViewById(R.id.firstName) as EditText
        lname = findViewById(R.id.lastName) as EditText
        toggleActionPromptText = findViewById(R.id.textView) as TextView

        viewSwitcher = findViewById(R.id.viewSwitcher) as ViewSwitcher


        viewSwitcher!!.inAnimation = _slideInLeft
        viewSwitcher!!.outAnimation = _slideOutright

        doActionButton = findViewById(R.id.doActionButton) as Button
        toggleActionButton = findViewById(R.id.toggleActionButton) as Button

        signUpNextButton = findViewById(R.id.signUpNextButton) as Button
        signUpPrevButton = findViewById(R.id.signUpPrevButton) as Button

        _progressDialog.setCancelable(false)

        toggleActionButton!!.setOnClickListener({ toggleAction() })

        doActionButton!!.setOnClickListener({ v ->
            _progressDialog.show()
            if (!_isSignUp) {
                _progressDialog.setMessage(getString(R.string.signin_progress_text))
                doLogin(v)
            } else {
                //if we are on the signup page, check the view to determine whether to switch to next view or attempt signup
                _logger.debug("displayed: " + viewSwitcher!!.displayedChild)
                if (viewSwitcher!!.displayedChild == 0) {
                    //First view, we want a next behavior
                    viewSwitcher!!.showNext()
                    doActionButton!!.visibility = View.VISIBLE
                } else {
                    //second view, attempt a signup action
                    _progressDialog.setMessage(getString(R.string.signup_progress_text))
                    //  doActionButton.setText(R.string.signup_button_text);
                    doSignUp(v)
                }
            }
        })

        //viewswitcher handler
        signUpNextButton!!.setOnClickListener({
            viewSwitcher!!.showNext()
            doActionButton!!.visibility = View.VISIBLE
            doActionButton!!.isClickable = true
        })

        signUpPrevButton!!.setOnClickListener({
            viewSwitcher!!.showPrevious()
            doActionButton!!.visibility = View.INVISIBLE
            doActionButton!!.isClickable = false
        });

        //gesture listener
        var gestureDetector: GestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            var tapCounter = 0;
            override fun onSingleTapConfirmed(e: MotionEvent?): Boolean {
                _logger.debug("View tapped! ${++tapCounter}");
                if (tapCounter == 10) {
                    EcoachBooksUtils.makeYesNoAlert(_activityContext, R.string.msg_https_disable_warning, { d, which ->
                        _logger.debug("Disabling HTTPS");
                        _preferences.enableUseHttp();
                        d.dismiss();
                    }).show();
                }
                return true;
            }
        })
        /*
        //removing
                username!!.setOnTouchListener { view, motionEvent ->
                    gestureDetector.onTouchEvent(motionEvent);
                    onTouchEvent(motionEvent);
                };*/
        _logger.debug("Should use http: ${_preferences.shouldUseHttp()}");

    }

    private fun doSignUp(view: View) {
        // mProgressDialog.show();
        //get credentials
        val pwd = password!!.text.toString()
        val uname = email!!.text.toString()

        val signUpRequest = SignUpRequest()
        signUpRequest.Username = email!!.text.toString()
        signUpRequest.Password = password!!.text.toString()
        signUpRequest.ConfirmPassword = password2!!.text.toString()
        signUpRequest.Phone = phone!!.text.toString()
        signUpRequest.Email = email!!.text.toString()
        signUpRequest.FirstName = fname!!.text.toString()
        signUpRequest.LastName = lname!!.text.toString()
        signUpRequest.School = school!!.text.toString()
        signUpRequest.Country = country!!.text.toString()

        var isValid = true

        //Validate
        if (signUpRequest.Password.isEmpty()) {
            isValid = false
            password!!.error = getString(R.string.PasswordRequiredError)
        }
        if (signUpRequest.ConfirmPassword.isEmpty()) {
            isValid = false
            password2!!.error = getString(R.string.Password2RequiredError)
        }
        if (signUpRequest.Phone.isEmpty()) {
            isValid = false
            phone!!.error = getString(R.string.PhoneRequiredError)
        }
        if (signUpRequest.Email.isEmpty()) {
            isValid = false
            email!!.error = getString(R.string.EmailRequiredError)
        }
        if (signUpRequest.FirstName.isEmpty()) {
            isValid = false
            fname!!.error = getString(R.string.FirstNameRequiredError)
        }
        if (signUpRequest.LastName.isEmpty()) {
            isValid = false
            lname!!.error = getString(R.string.LastNameRequiredError)
        }
        if (signUpRequest.School.isEmpty()) {
            isValid = false
            school!!.error = getString(R.string.SchoolRequiredError)
        }
        if (signUpRequest.Country.isEmpty()) {
            isValid = false
            country!!.error = getString(R.string.CountryRequiredError)
        }

        //ensure both provided passwords are the same

        if (signUpRequest.ConfirmPassword != signUpRequest.Password) {
            isValid = false
            password!!.error = getString(R.string.PasswordsDoNotMatchError)
            password2!!.error = getString(R.string.PasswordsDoNotMatchError)
        }


        if (!isValid) {
            showLoginError(R.string.LoginActivityValidationError)
            return
        }


        _apiSubscription = _ecoachBooksApi.register(signUpRequest)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ userValidationResult ->
                    userValidationResult.ecBooksAuth = _ecoachBooksApi.getAuthToken()
                    finishLogin(userValidationResult, uname, pwd)
                }, { throwable ->
                    _logger.error("There was an error trying to create the account :(", throwable);
                    throwable.printStackTrace()
                    finishLogin(null, null, null)
                })
    }

    private fun doLogin(view: View) {
        //  mProgressDialog.show();
        //get credentials
        val uname = username!!.text.toString()
        val pwd = password!!.text.toString()

        if (uname.isEmpty()) {
            username!!.error = getString(R.string.UsernameRequiredError)
        }
        if (pwd.isEmpty()) {
            password!!.error = getString(R.string.PasswordRequiredError)
        }

        if (!loginCredentialsNotEmpty(uname, pwd)) {
            showLoginError(R.string.LoginActivityValidationError)
            return
        }

        _apiSubscription = EcoachBooksApi.getInstance(this)
                .login(uname, pwd)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({ userValidationResult ->
                    userValidationResult.ecBooksAuth = _ecoachBooksApi.getAuthToken()
                    finishLogin(userValidationResult, uname, pwd)
                }, { throwable ->
                    _logger.error("Could not login.", throwable);
                    showLoginError(R.string.SignInError)
                })

    }


    private fun toggleAction() {
        _isSignUp = !_isSignUp
        //hide signin button
        if (_isSignUp) {
            //  doActionButton.setVisibility(View.GONE);
            //change button text to signup
            doActionButton!!.setText(R.string.signup_next_text)
            toggleActionButton!!.setText(R.string.signin_button_text)
            toggleActionPromptText!!.setText(R.string.signin_prompt_text)
            username!!.visibility = View.GONE
            email!!.visibility = View.VISIBLE
            phone!!.visibility = View.VISIBLE
            password2!!.visibility = View.VISIBLE
            doActionButton!!.visibility = View.INVISIBLE
            doActionButton!!.isClickable = false
            doActionButton!!.setText(R.string.signup_button_text)
            signUpNextButton!!.visibility = View.VISIBLE
        } else {
            //Sign In screen
            //  doActionButton.setVisibility(View.VISIBLE);
            username!!.visibility = View.VISIBLE
            email!!.visibility = View.GONE
            phone!!.visibility = View.GONE
            password2!!.visibility = View.GONE
            doActionButton!!.setText(R.string.signin_button_text)
            doActionButton!!.visibility = View.VISIBLE
            doActionButton!!.isClickable = true
            toggleActionButton!!.setText(R.string.signup_button_text)
            toggleActionPromptText!!.setText(R.string.signup_prompt_text)
            signUpNextButton!!.visibility = View.GONE

            //Force to first child
            viewSwitcher!!.displayedChild = 0
        }
    }

    private fun showLoginError(resource: Int) {
        Toast.makeText(this, resource, Toast.LENGTH_SHORT).show()
        _progressDialog.hide()
        //Scroll to the view with the error?
    }

    private fun loginCredentialsNotEmpty(uname: String, pwd: String): Boolean {
        return !uname.isEmpty() && !pwd.isEmpty()
    }

    private fun finishLogin(result: UserValidator.UserValidationResult?, uname: String?, pwd: String?) {

        if (result == null) {
            showLoginError(R.string.SignUpError)
            return
        }
        //Missing auth token means a validation failure.
        if (!result.success || result.ecBooksAuth.isEmpty()) {
            _logger.debug("Auth token was empty. Credentials supplied are probably not valid.")
            showLoginError(R.string.InvalidCredentialsError)
            return
        }

        val intent = Intent()
        intent.putExtra(AccountManager.KEY_ACCOUNT_NAME, uname)
        intent.putExtra(AccountManager.KEY_PASSWORD, pwd)
        intent.putExtra(AccountManager.KEY_ACCOUNT_TYPE, Constants.EC_BOOKS_ACCNT_TYPE)
        intent.putExtra(AccountManager.KEY_AUTHTOKEN, result.ecBooksAuth)
        intent.putExtra("ValidationResult", result.result)

        //  String authtokenType = Constants.EC_BOOKS_AUTH_TOKEN_TYPE;

        val account = Account(uname, intent.getStringExtra(AccountManager.KEY_ACCOUNT_TYPE))
        // if (getIntent().getBooleanExtra(ARG_IS_ADDING_NEW_ACCOUNT, false)) {
        //store username here
        val extraData = Bundle()
        extraData.putString("username", uname)
        // Creating the account on the device and setting the auth token we got
        // (Not setting the auth token will cause another call to the server to authenticate the user)
        _accountManager.addAccountExplicitly(account, pwd, extraData)
        _accountManager.setAuthToken(account, Constants.EC_BOOKS_ACCNT_TYPE, result.ecBooksAuth)
        /*   } else {
            mAccountManager.setPassword(account, accountPassword);
        }*/
        setAccountAuthenticatorResult(intent.extras)
        setResult(RESULT_OK, intent)
        // _spinner.setVisibility(View.INVISIBLE);
        _progressDialog.hide()
        //Set user as logged in
        if (SessionManager.logUserIn(this)) {
            goToMainContent()
        } else {
            _logger.error("Couldn't update user login state.")
        }
    }

    private fun goToMainContent() {
        //Should this fetch the user's books?

        _apiSubscription?.unsubscribe()
        _progressDialog.dismiss() // be gone! You shall not leak!!
        val intent = Intent(applicationContext, HomeActivity::class.java)
        intent.putExtra(Constants.IS_LOGIN_INTENT, true);
        startActivity(intent)
        finish()
    }
}
